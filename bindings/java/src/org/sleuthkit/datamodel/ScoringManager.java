/*
 * Sleuth Kit Data Model
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.datamodel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.sleuthkit.datamodel.Score.MethodCategory;
import org.sleuthkit.datamodel.Score.Significance;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbConnection;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;

/**
 * The scoring manager is responsible for updating and querying the score of
 * objects.
 *
 */
public class ScoringManager {

	private static final Logger LOGGER = Logger.getLogger(ScoringManager.class.getName());

	private final SleuthkitCase db;

	/**
	 * Construct a ScoringManager for the given SleuthkitCase.
	 *
	 * @param skCase The SleuthkitCase
	 *
	 */
	ScoringManager(SleuthkitCase skCase) {
		this.db = skCase;
	}

	/**
	 * Get the aggregate score for the given object.
	 *
	 * @param objId Object id.
	 *
	 * @return Score, if it is found, unknown otherwise.
	 *
	 * @throws TskCoreException
	 */
	public Score getAggregateScore(long objId) throws TskCoreException {
		try (CaseDbConnection connection = db.getConnection()) {
			return getAggregateScore(objId, connection);
		}
	}

	/**
	 * Get the aggregate score for the given object. Uses the connection from the
	 * given transaction.
	 *
	 * @param objId      Object id.
	 * @param transaction Transaction that provides the connection to use.
	 *
	 * @return Score, if it is found, unknown otherwise.
	 *
	 * @throws TskCoreException
	 */
	private Score getAggregateScore(long objId, CaseDbTransaction transaction) throws TskCoreException {
		CaseDbConnection connection = transaction.getConnection();
		return getAggregateScore(objId, connection);
	}

	/**
	 * Get the aggregate score for the given object.
	 *
	 * @param objId Object id.
	 * @param connection Connection to use for the query.
	 *
	 * @return Score, if it is found, SCORE_UNKNOWN otherwise.
	 *
	 * @throws TskCoreException
	 */
	Score getAggregateScore(long objId, CaseDbConnection connection) throws TskCoreException {
		String queryString = "SELECT significance, method_category FROM tsk_aggregate_score WHERE obj_id = " + objId;

		try {
			db.acquireSingleUserCaseReadLock();

			try (Statement s = connection.createStatement(); ResultSet rs = connection.executeQuery(s, queryString)) {
				if (rs.next()) {
					return new Score(Significance.fromID(rs.getInt("significance")), MethodCategory.fromID(rs.getInt("method_category")));
				} else {
					return Score.SCORE_UNKNOWN;
				}
			} catch (SQLException ex) {
				throw new TskCoreException("SQLException thrown while running query: " + queryString, ex);
			}
		} finally {
			db.releaseSingleUserCaseReadLock();
		}
	}

	/**
	 * Inserts or updates the score for the given object.
	 *
	 * @param objId Object id of the object.
	 * @param dataSourceObjectId Data source object id.
	 * @param score  Score to be inserted/updated.
	 * @param transaction Transaction to use for the update.
	 *
	 * @throws TskCoreException
	 */
	private void setAggregateScore(long objId, Long dataSourceObjectId, Score score, CaseDbTransaction transaction) throws TskCoreException {
		CaseDbConnection connection = transaction.getConnection();
		setAggregateScore(objId, dataSourceObjectId, score, connection);
	}

	/**
	 * Inserts or updates the score for the given object.
	 *
	 * @param objId              Object id of the object.
	 * @param dataSourceObjectId Data source object id, may be null.
	 * @param score              Score to be inserted/updated.
	 * @param connection         Connection to use for the update.
	 *
	 * @throws TskCoreException
	 */
	private void setAggregateScore(long objId, Long dataSourceObjectId, Score score, CaseDbConnection connection) throws TskCoreException {

		String insertSQLString = "INSERT INTO tsk_aggregate_score (obj_id, data_source_obj_id, significance , method_category) VALUES (?, ?, ?, ?)"
				+ " ON CONFLICT (obj_id) DO UPDATE SET significance = ?, method_category = ?";

		db.acquireSingleUserCaseWriteLock();
		try {
			PreparedStatement preparedStatement = connection.getPreparedStatement(insertSQLString, Statement.NO_GENERATED_KEYS);
			preparedStatement.clearParameters();

			preparedStatement.setLong(1, objId);
			if (dataSourceObjectId != null) {
				preparedStatement.setLong(2, dataSourceObjectId);
			} else {
				preparedStatement.setNull(2, java.sql.Types.NULL);
			}
			preparedStatement.setInt(3, score.getSignificance().getId());
			preparedStatement.setInt(4, score.getMethodCategory().getId());

			preparedStatement.setInt(5, score.getSignificance().getId());
			preparedStatement.setInt(6, score.getMethodCategory().getId());

			connection.executeUpdate(preparedStatement);
		} catch (SQLException ex) {
			throw new TskCoreException(String.format("Error updating aggregate score, query: %s for objId = %d", insertSQLString, objId), ex);//NON-NLS
		} finally {
			db.releaseSingleUserCaseWriteLock();
		}

	}



	/**
	 * Updates the score for the specified object after a result has been
	 * added. Is optimized to do nothing if the new score is less than the
	 * current aggregate score. 
	 *
	 * @param objId              Object id.
	 * @param dataSourceObjectId Object id of the data source, may be null.
	 * @param newResultScore        Score for a newly added analysis result.
	 * @param transaction        Transaction to use for the update.
	 *
	 * @return Aggregate score for the object.
	 *
	 * @throws TskCoreException
	 */
	Score updateAggregateScoreAfterAddition(long objId, Long dataSourceObjectId, Score newResultScore, CaseDbTransaction transaction) throws TskCoreException {

		/* get an exclusive write lock on the DB before we read anything so that we know we are
		 * the only one reading existing scores and updating.  The risk is that two computers
		 * could update the score and the aggregate score ends up being incorrect. 
		 * 
		 * NOTE: The alternative design is to add a 'version' column for opportunistic locking
		 * and calculate these outside of a transaction.  We opted for table locking for performance
		 * reasons so that we can still add the analysis results in a batch.  That remains an option
		 * if we get into deadlocks with the current design. 
		 */
		try {
			CaseDbConnection connection = transaction.getConnection();
			connection.getAggregateScoreTableWriteLock();
		} catch (SQLException ex) {
			throw new TskCoreException("Error getting exclusive write lock on aggregate score table", ex);//NON-NLS
		}
			
		
		// Get the current score 
		Score currentAggregateScore = ScoringManager.this.getAggregateScore(objId, transaction);

		// If current score is Unknown And newscore is not Unknown - allow None (good) to be recorded
		// or if the new score is higher than the current score
		if  ( (currentAggregateScore.compareTo(Score.SCORE_UNKNOWN) == 0 && newResultScore.compareTo(Score.SCORE_UNKNOWN) != 0)
			  || (Score.getScoreComparator().compare(newResultScore, currentAggregateScore) > 0)) {
			setAggregateScore(objId, dataSourceObjectId, newResultScore, transaction);
			
			// register score change in the transaction.
			transaction.registerScoreChange(new ScoreChange(objId, dataSourceObjectId, currentAggregateScore, newResultScore));
			return newResultScore;
		} else {
			// return the current score
			return currentAggregateScore;
		}
	}
	
	/**
	 * Recalculate the aggregate score after an analysis result was 
	 * deleted.
	 * 
	 * @param objId Content that had result deleted from
	 * @param dataSourceObjectId Data source content is in
	 * @param transaction 
	 * @return New Score
	 * @throws TskCoreException 
	 */
	Score updateAggregateScoreAfterDeletion(long objId, Long dataSourceObjectId, CaseDbTransaction transaction) throws TskCoreException {

		CaseDbConnection connection = transaction.getConnection();
		
		/* get an exclusive write lock on the DB before we read anything so that we know we are
		 * the only one reading existing scores and updating.  The risk is that two computers
		 * could update the score and the aggregate score ends up being incorrect. 
		 * 
		 * NOTE: The alternative design is to add a 'version' column for opportunistic locking
		 * and calculate these outside of a transaction.  We opted for table locking for performance
		 * reasons so that we can still add the analysis results in a batch.  That remains an option
		 * if we get into deadlocks with the current design. 
		 */
		try {
			connection.getAggregateScoreTableWriteLock();
		} catch (SQLException ex) {
			throw new TskCoreException("Error getting exclusive write lock on aggregate score table", ex);//NON-NLS
		}
			
		// Get the current score 
		Score currentScore = ScoringManager.this.getAggregateScore(objId, transaction);

		// Calculate the score from scratch by getting all of them and getting the highest
		List<AnalysisResult> analysisResults = db.getBlackboard().getAnalysisResults(objId, connection);
		Score newScore = Score.SCORE_UNKNOWN;
		for (AnalysisResult iter : analysisResults) {
			Score iterScore = iter.getScore();
			if (Score.getScoreComparator().compare(iterScore, newScore) > 0) {
				newScore = iterScore;
			}
		}

		// get the maximum score of the calculated aggregate score of analysis results
		// or the score derived from the maximum known status of a content tag on this content.
		Optional<Score> tagScore = getTagKnownStatus(objId)
				.map(knownStatus -> TaggingManager.getTagScore(knownStatus));
		
		if (tagScore.isPresent() && Score.getScoreComparator().compare(tagScore.get(), newScore) > 0) {
			newScore = tagScore.get();
		}
		
		// only change the DB if we got a new score. 
		if (newScore.compareTo(currentScore) != 0) {
			setAggregateScore(objId, dataSourceObjectId, newScore, connection);

			// register the score change with the transaction so an event can be fired for it. 
			transaction.registerScoreChange(new ScoreChange(objId, dataSourceObjectId, currentScore, newScore));
		}
		return newScore;
	}
	
	/**
	 * Retrieves the maximum FileKnown status of any tag associated with the content id.
	 * @param contentId The object id of the content.
	 * @return The maximum FileKnown status for this content or empty.
	 * @throws TskCoreException 
	 */
	private Optional<TskData.FileKnown> getTagKnownStatus(long contentId) throws TskCoreException {
		String queryString = "SELECT tag_names.knownStatus AS knownStatus "
			 + " FROM content_tags "
			+ " INNER JOIN tag_names ON content_tags.tag_name_id = tag_names.tag_name_id "
			+ "	WHERE content_tags.obj_id = " + contentId 
			+ " ORDER BY tag_names.knownStatus DESC "
			+ " LIMIT 1 ";

		db.acquireSingleUserCaseReadLock();
		try (CaseDbConnection connection = db.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = connection.executeQuery(statement, queryString);) {

			if (resultSet.next()) {
				return Optional.ofNullable(TskData.FileKnown.valueOf(resultSet.getByte("knownStatus")));
			} else {
				return Optional.empty();
			}
	
		} catch (SQLException ex) {
			throw new TskCoreException("Error getting content tag FileKnown status for content with id: " + contentId);
		} finally {
			db.releaseSingleUserCaseReadLock();
		}
	}

	/**
	 * Get the count of contents within the specified data source
	 * with the specified significance.
	 *
	 * @param dataSourceObjectId Data source object id.
	 * @param significance Significance to look for.
	 *
	 * @return Number of contents with given score.
	 * @throws TskCoreException if there is an error getting the count. 
	 */
	public long getContentCount(long dataSourceObjectId, Score.Significance significance) throws TskCoreException {
		try (CaseDbConnection connection = db.getConnection()) {
			return getContentCount(dataSourceObjectId, significance, connection);
		} 
	}


	/**
	 * Get the count of contents with the specified significance. Uses the
	 * specified database connection.
	 *
	 * @param dataSourceObjectId Data source object id.
	 * @param significance       Significance to look for.
	 * @param connection         Database connection to use..
	 *
	 * @return Number of contents with given score.
	 *
	 * @throws TskCoreException if there is an error getting the count.
	 */
	private long getContentCount(long dataSourceObjectId, Score.Significance significance, CaseDbConnection connection) throws TskCoreException {
		String queryString = "SELECT COUNT(obj_id) AS count FROM tsk_aggregate_score"
				+ " WHERE data_source_obj_id = " + dataSourceObjectId 
				+ " AND significance = " + significance.getId();

		db.acquireSingleUserCaseReadLock();
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = connection.executeQuery(statement, queryString);) {

			long count = 0;
			if (resultSet.next()) {
				count = resultSet.getLong("count");
			}
			return count;
		} catch (SQLException ex) {
			throw new TskCoreException("Error getting count of items with significance = " + significance.toString(), ex);
		} finally {
			db.releaseSingleUserCaseReadLock();
		}
	}
	
	/**
	 * Get the contents with the specified score.
	 *
	 * @param dataSourceObjectId Data source object id.
	 * @param significance       Significance to look for.
	 *
	 * @return Collection of contents with given score.
	 * 
	 * @throws TskCoreException if there is an error getting the contents.
	 */
	public List<Content> getContent(long dataSourceObjectId, Score.Significance significance) throws TskCoreException {
		try (CaseDbConnection connection = db.getConnection()) {
			return getContent(dataSourceObjectId, significance, connection);
		} 
	}

	/**
	 * Gets the contents with the specified score. Uses the specified
	 * database connection.
	 *
	 * @param dataSourceObjectId Data source object id.
	 * @param significance       Significance to look for.
	 * @param connection         Connection to use for the query.
	 *
	 * @return List of contents with given score.
	 *
	 * @throws TskCoreException
	 */
	private List<Content> getContent(long dataSourceObjectId, Score.Significance significance, CaseDbConnection connection) throws TskCoreException {
		String queryString = "SELECT obj_id FROM tsk_aggregate_score"
				+ " WHERE data_source_obj_id = " + dataSourceObjectId 
				+ " AND significance = " + significance.getId();
			
		db.acquireSingleUserCaseReadLock();
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = connection.executeQuery(statement, queryString);) {

			List<Content> items = new ArrayList<>();
			while (resultSet.next()) {
				long objId = resultSet.getLong("obj_id");
				items.add(db.getContentById(objId));
			}
			return items;
		} catch (SQLException ex) {
			throw new TskCoreException("Error getting list of items with significance = " + significance.toString(), ex);
		} finally {
			db.releaseSingleUserCaseReadLock();
		}
	}
}
