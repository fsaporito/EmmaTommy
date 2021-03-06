package emmaTommy.DBClient.Actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import emmaTommy.DBClient.ActorsMessages.Queries.AcquireDBLock;
import emmaTommy.DBClient.ActorsMessages.Queries.GetAllServiziInCollection;
import emmaTommy.DBClient.ActorsMessages.Queries.GetAllServiziInCollectionByProperties;
import emmaTommy.DBClient.ActorsMessages.Queries.GetCollectionList;
import emmaTommy.DBClient.ActorsMessages.Queries.GetServizioByID;
import emmaTommy.DBClient.ActorsMessages.Queries.IsDBAlive;
import emmaTommy.DBClient.ActorsMessages.Queries.IsDBLocked;
import emmaTommy.DBClient.ActorsMessages.Queries.IsServizioByIDPresent;
import emmaTommy.DBClient.ActorsMessages.Queries.MoveServizioByID;
import emmaTommy.DBClient.ActorsMessages.Queries.ReleaseDBLock;
import emmaTommy.DBClient.ActorsMessages.Queries.RemoveServizioByID;
import emmaTommy.DBClient.ActorsMessages.Queries.UpdateServizioByID;
import emmaTommy.DBClient.ActorsMessages.Queries.UpdateServizioEnrichedByID;
import emmaTommy.DBClient.ActorsMessages.Queries.WriteNewServizioByID;
import emmaTommy.DBClient.ActorsMessages.Queries.WriteNewServizioEnrichedByID;
import emmaTommy.DBClient.ActorsMessages.Replies.CollectionListSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.DBFailedToBeLocked;
import emmaTommy.DBClient.ActorsMessages.Replies.DBIsAlive;
import emmaTommy.DBClient.ActorsMessages.Replies.DBIsAlreadyLocked;
import emmaTommy.DBClient.ActorsMessages.Replies.DBIsLockedByYou;
import emmaTommy.DBClient.ActorsMessages.Replies.DBLockAcquired;
import emmaTommy.DBClient.ActorsMessages.Replies.DBLockReleased;
import emmaTommy.DBClient.ActorsMessages.Replies.DBManagerActive;
import emmaTommy.DBClient.ActorsMessages.Replies.DBManagerErrorState;
import emmaTommy.DBClient.ActorsMessages.Replies.DBManagerStatus;
import emmaTommy.DBClient.ActorsMessages.Replies.DBOperationFaillure;
import emmaTommy.DBClient.ActorsMessages.Replies.MoveServizioByIDSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.RemoveServizioByIDSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.DBIsNotAlive;
import emmaTommy.DBClient.ActorsMessages.Replies.DBIsNotLocked;
import emmaTommy.DBClient.ActorsMessages.Replies.Reply;
import emmaTommy.DBClient.ActorsMessages.Replies.ReplyServiziInCollection;
import emmaTommy.DBClient.ActorsMessages.Replies.ReplyServiziInCollectionEnriched;
import emmaTommy.DBClient.ActorsMessages.Replies.ReplyServizioById;
import emmaTommy.DBClient.ActorsMessages.Replies.ReplyServizioByIdEnriched;
import emmaTommy.DBClient.ActorsMessages.Replies.ServizioByIDFound;
import emmaTommy.DBClient.ActorsMessages.Replies.ServizioByIDNotFound;
import emmaTommy.DBClient.ActorsMessages.Replies.UpdateServizioByIDEnrichedSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.UpdateServizioByIDSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.WriteNewServizioByIDEnrichedSuccess;
import emmaTommy.DBClient.ActorsMessages.Replies.WriteNewServizioByIDSuccess;
import emmaTommy.TommyDataModel.TommyEnrichedJSON;
import emmaTommy.TommyDataModel.Factories.ServizioQueryField;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class DBClientAPI {

	protected org.apache.logging.log4j.Logger logger;

	public DBClientAPI() {
		this.logger = LogManager.getLogger("Root");
	}

	public DBClientAPI(String loggerName) {
		if (loggerName == null) {
			throw new NullPointerException("Received null logger name");
		}
		if (loggerName.isBlank()) {
			throw new IllegalArgumentException("Received blanck logger name");
		}
		this.logger = LogManager.getLogger(loggerName);
	}
	
	
	/**
	 * Acquire DB Lock (Infinite Trials,Timeout beetween each AcquireLock Query is given also by operationTimeOutSecs)
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @throws DBOperationFailedException 
	 */
	public void acquireDBLockInfiniteLoop(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		this.acquireDBLockInfiniteLoop(client, clientID, dbManager, operationTimeOutSecs, operationTimeOutSecs);
	}

	/**
	 * Acquire DB Lock (Infinite Trials)
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param timeoutBetweenLockRequestsSecs Timeout beetween each AcquireLock Query
	 * @throws DBOperationFailedException 
	 */
	public void acquireDBLockInfiniteLoop(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, int timeoutBetweenLockRequestsSecs) throws DBOperationFailedException {
		this.acquireDBLock(client, clientID, dbManager, operationTimeOutSecs, timeoutBetweenLockRequestsSecs, -1);
	}

	/**
	 * Acquire DB Lock
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param timeoutBetweenLockRequestsSecs Timeout beetween each AcquireLock Query
	 * @param maxTrialsNum Max Number of AcquireLock Operations (-1 for infinite)
	 * @throws DBOperationFailedException 
	 */
	public void acquireDBLock(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, int timeoutBetweenLockRequestsSecs, int maxTrialsNum) throws DBOperationFailedException {

		String method_name = "::acquireDBLock(): ";

		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		if (timeoutBetweenLockRequestsSecs < 0) {
			throw new DBOperationFailedException("Received TimeOut Between Lock Requests was negative");
		}
		if (maxTrialsNum < 0 && maxTrialsNum != -1) {
			throw new DBOperationFailedException(
					"Received Max Trials Num was negative (Only -1 is permitted as infinite trials)");
		}
		if (maxTrialsNum == 0) {
			throw new DBOperationFailedException("Received Max Trials Num was zero");
		}

		// Initialize
		int trialNums = 0;
		Boolean lockAcquired = false;
		logger.trace(method_name + "Asking for " + dbManager.path().name() + " Lock (" 
								 + " " + "timeoutBetweenLockRequestsSecs: " + timeoutBetweenLockRequestsSecs
								 + " " + "maxTrialsNum: " + maxTrialsNum
								 + ")"); 

		// Lock Procedure
		while (!lockAcquired) {

			// Check Trials
			trialNums += 1;
			if (maxTrialsNum != -1 && trialNums > maxTrialsNum) {
				throw new DBOperationFailedException(
						"Reached Max Trials for Locking Operation Num (" + maxTrialsNum + ")");
			}

			Future<Object> futureLock = Patterns.ask(dbManager, new AcquireDBLock(client.path().name(), clientID), 1000);

			try {

				Reply replyLock = (Reply) Await.result(futureLock,
						Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));

				if (replyLock instanceof DBLockAcquired) {
					lockAcquired = true;
					logger.trace(method_name + "Acquired DB Lock (Tentative " + trialNums + ")");
				} else if (replyLock instanceof DBIsAlreadyLocked) {
					throw new IllegalArgumentException(((DBIsAlreadyLocked) replyLock).getCause());
				} else if (replyLock instanceof DBFailedToBeLocked) {
					throw new DBOperationFailedException(((DBFailedToBeLocked) replyLock).getCause());
				} else if (replyLock instanceof DBOperationFaillure) {
					throw new DBOperationFailedException(((DBOperationFaillure) replyLock).getCause());
				} else {
					throw new DBOperationFailedException("Received unhandled reply of type: " + replyLock.getReplyTypeName());
				}

			} catch (Exception e) {

				logger.error(
						method_name + "Failed to Acquire DB Lock from " + dbManager.path().name() 
									+ " (Tentative " + trialNums + "): " + e.getMessage());
				try {
					logger.error(method_name + "Will sleep for " + timeoutBetweenLockRequestsSecs + " seconds");
					Thread.sleep(timeoutBetweenLockRequestsSecs * 1000);
				} catch (InterruptedException e1) {
					logger.error(method_name + " Interrupted my sleep : " + e1.getMessage());
				}

			}
		}

	}

	
	/**
	 * Checks if the calling client owns the DB Lock 
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @throws DBOperationFailedException 
	 * @return True if the calling client owns the DB lock. False otherwise (If the DB isn't locked, the result will be false).
	 */
	public Boolean doesClientOwnsDBLock(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		return client.path().name().compareToIgnoreCase(this.getDBLockOwner(client, clientID, dbManager, operationTimeOutSecs)) == 0;
	}
	
	
	/**
	 * Asks for the List of Collection in the DB
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @throws DBOperationFailedException 
	 * @return The ArrayList containing all the found collections on the DB. This list is never null, but can be Empty (No collections found)
	 */
	public ArrayList<String> getCollectionList(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::getCollectionList(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		ArrayList<String> collectionList = new ArrayList<String>();
		Future<Object> futureCollectionList = Patterns.ask(dbManager, 
															new GetCollectionList(client.path().name(), clientID), 
															1000);

		try {

			Reply replyCollectionList = (Reply) Await.result(futureCollectionList, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyCollectionList instanceof CollectionListSuccess) {
				collectionList = ((CollectionListSuccess) replyCollectionList).getCollectionList();
				logger.trace(method_name + "Got " + collectionList.size() + " collection in DB");
			} else if (replyCollectionList instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyCollectionList).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyCollectionList.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to get CollectionList: " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
		return collectionList;
	}
	
	
	/**
	 * Asks for DB Lock Owner
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @throws DBOperationFailedException 
	 * @return The DB Lock Owner Name. Empty String if the DB is not locked
	 */
	public String getDBLockOwner(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::getDBLockOwner(): ";
		String lockOwner = "";
		Future<Object> futureDBAlive = Patterns.ask(dbManager, new IsDBLocked(client.path().name(), clientID), 1000);
		try {
			Reply replyReleaseLock = (Reply) Await.result(futureDBAlive,
					Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyReleaseLock instanceof DBIsAlreadyLocked) {
				lockOwner = ((DBIsAlreadyLocked) replyReleaseLock).getLockOwnerName();
				logger.trace(method_name + ((DBIsAlreadyLocked) replyReleaseLock).getCause());
			} else if (replyReleaseLock instanceof DBIsLockedByYou) {
				lockOwner = client.path().name();
				logger.trace(method_name + "DB is Locked by The Calling Client " + lockOwner);
			} else if (replyReleaseLock instanceof DBIsNotLocked) {
				lockOwner = "";
				logger.trace(method_name + "DB is Not Locked");
			} else if (replyReleaseLock instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyReleaseLock).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyReleaseLock.getReplyTypeName());
			}
		} catch (Exception e) {
			String error_msg = "Failed to get DB Lock Owner: " + e.getMessage();
			logger.error(method_name + error_msg);
			throw new DBOperationFailedException(error_msg);
		}
		return lockOwner;
	}
	
	
	/**
	 * Get a Servizio Item from the DB
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the wanted Servizio
	 * @param collectionName Name of the collection where the Servizio should be found
	 * @return The wanted servizio. Null if the servizio isn't found in the collection.
	 * @throws DBOperationFailedException
	 */
	public TommyEnrichedJSON getServizioByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String collectionName) throws DBOperationFailedException {
		String method_name = "::getServizioByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		TommyEnrichedJSON servizioEnriched = new TommyEnrichedJSON();
		Future<Object> futureGetServizio = Patterns.ask(dbManager, 
														new GetServizioByID(client.path().name(), clientID, servizioID, collectionName), 
														10000);

		try {

			Reply replyGetServizio = (Reply) Await.result(futureGetServizio, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyGetServizio instanceof ReplyServizioById) {
				String servizioJSON = ((ReplyServizioById) replyGetServizio).getServizioJSON();
				servizioEnriched.setJsonServizio(servizioJSON);
			} else if (replyGetServizio instanceof ReplyServizioByIdEnriched) {
				servizioEnriched = ((ReplyServizioByIdEnriched) replyGetServizio).getEnrichedServizio();
			} else if (replyGetServizio instanceof ServizioByIDNotFound) {
				servizioEnriched = null;
			} else if (replyGetServizio instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyGetServizio).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyGetServizio.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to get servizio " + servizioID + " from collection " + collectionName + ": " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
		return servizioEnriched;
	}
	
	/**
	 * Get a Servizi List from the DB with the given property
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param propName PropertyName of the wanted servizi
	 * @param propValue PropertyValue of the wanted servizi
	 * @param collectionName Name of the collection where the Servizio should be found
	 * @return The wanted servizio. Null if the servizio isn't found in the collection.
	 * @throws DBOperationFailedException
	 */
	public  ArrayList<TommyEnrichedJSON> getAllServiziInCollectionByProperty(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, ServizioQueryField propName, String propValue, String collectionName) throws DBOperationFailedException {
		String method_name = "::getAllServiziInCollectionByProperty(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		ArrayList<TommyEnrichedJSON> serviziListEnriched = new ArrayList<TommyEnrichedJSON>();
		Future<Object> futureGetServizi = Patterns.ask(dbManager, 
														new GetAllServiziInCollectionByProperties(client.path().name(), clientID, propName, propValue, collectionName), 
														10000);

		try {

			Reply replyGetServizi = (Reply) Await.result(futureGetServizi, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyGetServizi instanceof ReplyServiziInCollection) {
				HashMap<String, String> serviziMap = ((ReplyServiziInCollection) replyGetServizi).getServiziMap();
				for (String servizioID: serviziMap.keySet()) {
					if (servizioID == null) {
						throw new NullPointerException("Found a null key in serviziMap");
					}
					String servizio = serviziMap.get(servizioID);
					if (servizio == null) {
						throw new NullPointerException("Found a null servizio associated to ID " +  servizioID + " in serviziMap");
					}
					if (servizio.isBlank()) {
						throw new NullPointerException("Found a blanck servizio associated to ID " +  servizioID + " in serviziMap");
					}
					TommyEnrichedJSON servizioEnriched = new TommyEnrichedJSON(servizioID, servizio);
					serviziListEnriched.add(servizioEnriched);
				}				
			} else if (replyGetServizi instanceof ReplyServiziInCollectionEnriched) {
				serviziListEnriched.addAll(((ReplyServiziInCollectionEnriched) replyGetServizi).getServiziMap().values());
			} else if (replyGetServizi instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyGetServizi).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyGetServizi.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to get servizi from collection " + collectionName + ": " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
		return serviziListEnriched;
	}
	
	/**
	 * Get a Servizi List from the DB with the given property
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param propNames PropertyNames list of the wanted servizi
	 * @param propValues PropertyValues list of the wanted servizi
	 * @param collectionName Name of the collection where the Servizio should be found
	 * @return The wanted servizio. Null if the servizio isn't found in the collection.
	 * @throws DBOperationFailedException
	 */
	public  ArrayList<TommyEnrichedJSON> getAllServiziInCollectionByProperties(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, TreeMap<ServizioQueryField, String> propNamesValuesMap, String collectionName) throws DBOperationFailedException {
		String method_name = "::getAllServiziInCollectionByProperties(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		ArrayList<TommyEnrichedJSON> serviziListEnriched = new ArrayList<TommyEnrichedJSON>();
		Future<Object> futureGetServizi = Patterns.ask(dbManager, 
														new GetAllServiziInCollectionByProperties(client.path().name(), clientID, propNamesValuesMap, collectionName), 
														10000);

		try {

			Reply replyGetServizi = (Reply) Await.result(futureGetServizi, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyGetServizi instanceof ReplyServiziInCollection) {
				HashMap<String, String> serviziMap = ((ReplyServiziInCollection) replyGetServizi).getServiziMap();
				if (serviziMap.isEmpty())
				{
					logger.error("Received Empty Map");
				}
				for (String servizioID: serviziMap.keySet()) {
					if (servizioID == null) {
						throw new NullPointerException("Found a null key in serviziMap");
					}
					String servizio = serviziMap.get(servizioID);
					if (servizio == null) {
						throw new NullPointerException("Found a null servizio associated to ID " +  servizioID + " in serviziMap");
					}
					if (servizio.isBlank()) {
						throw new NullPointerException("Found a blanck servizio associated to ID " +  servizioID + " in serviziMap");
					}
					TommyEnrichedJSON servizioEnriched = new TommyEnrichedJSON(servizioID, servizio);
					serviziListEnriched.add(servizioEnriched);
				}				
			} else if (replyGetServizi instanceof ReplyServiziInCollectionEnriched) {
				serviziListEnriched.addAll(((ReplyServiziInCollectionEnriched) replyGetServizi).getServiziMap().values());
			} else if (replyGetServizi instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyGetServizi).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyGetServizi.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to get servizi from collection " + collectionName + ": " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
		return serviziListEnriched;
	}
	

	/**
	 * Get all the Servizi in the given Collection from the DB
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param collectionName Name of the collection where the Servizio should be found
	 * @return The wanted servizio. Null if the servizio isn't found in the collection.
	 * @throws DBOperationFailedException
	 */
	public ArrayList<TommyEnrichedJSON> getAllServiziInCollection(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String collectionName) throws DBOperationFailedException {
		String method_name = "::getAllServiziInCollection(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		ArrayList<TommyEnrichedJSON> serviziListEnriched = new ArrayList<TommyEnrichedJSON>();
		Future<Object> futureGetServizi = Patterns.ask(dbManager, 
														new GetAllServiziInCollection(client.path().name(), clientID, collectionName), 
														1000);

		try {

			Reply replyGetServizi = (Reply) Await.result(futureGetServizi, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyGetServizi instanceof ReplyServiziInCollection) {
				HashMap<String, String> serviziMap = ((ReplyServiziInCollection) replyGetServizi).getServiziMap();
				for (String servizioID: serviziMap.keySet()) {
					if (servizioID == null) {
						throw new NullPointerException("Found a null key in serviziMap");
					}
					String servizio = serviziMap.get(servizioID);
					if (servizio == null) {
						throw new NullPointerException("Found a null servizio associated to ID " +  servizioID + " in serviziMap");
					}
					if (servizio.isBlank()) {
						throw new NullPointerException("Found a blanck servizio associated to ID " +  servizioID + " in serviziMap");
					}
					TommyEnrichedJSON servizioEnriched = new TommyEnrichedJSON(servizioID, servizio);
					serviziListEnriched.add(servizioEnriched);
				}
				
				
			} else if (replyGetServizi instanceof ReplyServiziInCollectionEnriched) {
				serviziListEnriched.addAll(((ReplyServiziInCollectionEnriched) replyGetServizi).getServiziMap().values());
			} else if (replyGetServizi instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyGetServizi).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyGetServizi.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to get servizi from collection " + collectionName + ": " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
		return serviziListEnriched;
	}
	
	
	
	/**
	 * Check if the collection is present in the DB
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param collectionName Name of the collection
	 * @return True if the collection is present in the DB, false otherwise
	 * @throws DBOperationFailedException
	 */
	public Boolean isCollectionByNamePresent(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String collectionName) throws DBOperationFailedException {
		return (this.getCollectionList(client, clientID, dbManager, operationTimeOutSecs)).contains(collectionName);
	}
	
	
	/**
	 * Checks if the DB is Alive
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @return True if the DB is alive, false otherwise
	 * @throws DBOperationFailedException
	 */
	public Boolean isDBAlive(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::isDBAlive(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureDBAlive = Patterns.ask(dbManager, new IsDBAlive(client.path().name(), clientID), 1000);
		try {
			Reply replyReleaseLock = (Reply) Await.result(futureDBAlive, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyReleaseLock instanceof DBIsAlive) {
				logger.trace(method_name + "DB is Alive");
				return true;
			} else if (replyReleaseLock instanceof DBIsNotAlive) {
				logger.warn(method_name + "DB is Not Alive");
				return false;
			} else if (replyReleaseLock instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyReleaseLock).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyReleaseLock.getReplyTypeName());
			}
		} catch (Exception e) {
			String error_msg = "Failed to check if the DB was alive: " + e.getMessage();
			logger.error(method_name + error_msg);
			throw new DBOperationFailedException(error_msg);
		}
	}
	
	
	/**
	 * Checks if the DB is Locked
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @return True if the DB is Locked, false otherwise
	 * @throws DBOperationFailedException
	 */
	public Boolean isDBLocked(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::isDBAlive(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Boolean isDBLocked = true;
		String lockOwner = this.getDBLockOwner(client, clientID, dbManager, operationTimeOutSecs);
		if (lockOwner == null) {
			logger.error(method_name + "Received DBLockOwner was null");
			throw new DBOperationFailedException("Received DBLockOwner was null");
		} else {
			isDBLocked = lockOwner.isBlank();
			if (isDBLocked) {
				logger.trace(method_name + "DB is Locked by " + lockOwner);
			} else {
				logger.trace(method_name + "DB is not Locked");
			}
		}
		return isDBLocked;
	}
	
	
	/**
	 * Checks that the DBManager Actor is in Active Status
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @return True if the DBManager Actor is in Active Status, false otherwise (for any other status)
	 * @throws DBOperationFailedException
	 */
	public Boolean isDBManagerActive(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::isDBManagerActive(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Boolean isDBManagerActiveFlag = false;
		Future<Object> futureDBManagerActive = Patterns.ask(dbManager, new IsDBAlive(client.path().name(), clientID), 1000);
		try {
			Reply replyDBManagerActive = (Reply) Await.result(futureDBManagerActive, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyDBManagerActive instanceof DBManagerStatus) {
				DBManagerStatus status = (DBManagerStatus) replyDBManagerActive;
				if (status instanceof DBManagerErrorState) {
					logger.error(method_name + "DBManager Actor Status Was: " + status.getStatus());
				} else {
					logger.trace(method_name + "DBManager Actor Status Was: " + status.getStatus());
				}
				if (status instanceof DBManagerActive) {
					isDBManagerActiveFlag = true;					
				} else {
					isDBManagerActiveFlag = false;
				}			
			} else if (replyDBManagerActive instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyDBManagerActive).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyDBManagerActive.getReplyTypeName());
			}
		} catch (Exception e) {
			String error_msg = "Failed to get the DB Manager Status: " + e.getMessage();
			logger.error(method_name + error_msg);
			throw new DBOperationFailedException(error_msg);
		}		
		return isDBManagerActiveFlag;
	}
	
	/**
	 * Checks that a Servizio Item exists in the given Collectin in the DB
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the wanted Servizio
	 * @param collectionName Name of the collection where the Servizio should be found
	 * @return True if the servizio is found, false otherwise
	 * @throws DBOperationFailedException
	 */
	public Boolean isServizioByIdPresent(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String collectionName) throws DBOperationFailedException {
		String method_name = "::isServizioByIdPresent(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureCheckServizio = Patterns.ask(dbManager, 
														new IsServizioByIDPresent(client.path().name(), clientID, servizioID, collectionName), 
														10000);

		try {

			Reply replyCheckServizio = (Reply) Await.result(futureCheckServizio, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyCheckServizio instanceof ServizioByIDFound) {
				return true;
			} else if (replyCheckServizio instanceof ServizioByIDNotFound) {
				return false;
			} else if (replyCheckServizio instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyCheckServizio).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyCheckServizio.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to check wheter servizio " + servizioID + " exists in collection " + collectionName + ": " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
	}
	
	/**
	 * Move a Servizio Between Collections
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param oldCollectionName Collection where the Servizio is to be found initially
	 * @param newCollectionName Collection where the Servizio is to be moved
	 * @throws DBOperationFailedException
	 * 
	 */
	public void moveServizioByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String oldCollectionName, String newCollectionName) throws DBOperationFailedException {
		String method_name = "::moveServizioByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureMoveServizio = Patterns.ask(dbManager, 
														new MoveServizioByID(client.path().name(), clientID, servizioID, oldCollectionName, newCollectionName), 
														1000);

		try {

			Reply replyMoveServizio = (Reply) Await.result(futureMoveServizio, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			
			if (replyMoveServizio instanceof MoveServizioByIDSuccess) {
				logger.trace(method_name + "Servizio " + servizioID 
										 + " moved correctly from collection " 
										 + oldCollectionName + " to collection"
										 + newCollectionName
										 );		
			} else if (replyMoveServizio instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyMoveServizio).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyMoveServizio.getReplyTypeName());
			}
			
		} catch (Exception e) {
			String error_msg = "Failed to move Servizio " + servizioID + " : " + e.getMessage();
			logger.error(method_name + error_msg);			
			throw new DBOperationFailedException(error_msg);
		}
	}
	
	
	/**
	 * Move a Servizio Between Collections
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param oldCollectionName Collection where the Servizio is to be found initially
	 * @param newCollectionName Collection where the Servizio is to be moved
	 * @throws DBOperationFailedException
	 * 
	 */
	public void releaseDBLock(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs) throws DBOperationFailedException {
		String method_name = "::releaseDBLock(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureReleaseLock = Patterns.ask(dbManager, new ReleaseDBLock(client.path().name(), clientID), 1000);
		try {
			Reply replyReleaseLock = (Reply) Await.result(futureReleaseLock, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyReleaseLock instanceof DBLockReleased) {
				logger.trace(method_name + "Lock Released");
			} else if (replyReleaseLock instanceof DBIsNotLocked) {
				logger.trace(method_name + "Lock Released (It wasn't locked)");
			} else if (replyReleaseLock instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyReleaseLock).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyReleaseLock.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to release lock for the " + dbManager.path().name() + " DB owned by "
					+ client.path().name() + ": " + e.getMessage());
		}
	}	
	

	/**
	 * Remove Servizio to the given Collection
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to remove
	 * @param collectionName Collection where the Servizio is to be found
	 * @throws DBOperationFailedException
	 * 
	 */
	public void removeServizioByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String collectionName) throws DBOperationFailedException {
		String method_name = "::writeNewServizioByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureRemove = Patterns.ask(dbManager, 
													new RemoveServizioByID(client.path().name(), clientID, servizioID, collectionName), 
													1000);
		try {
			Reply replyRemove = (Reply) Await.result(futureRemove, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyRemove instanceof RemoveServizioByIDSuccess) {
				logger.trace(method_name + "Wrote new servizio " + servizioID + " from collection " + collectionName);
			} else if (replyRemove instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyRemove).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyRemove.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to remove servizio " + servizioID + " from collection " + collectionName
									 + ": " + e.getMessage());
		}
	}

	
	/**
	 * Update a Servizio with new Data
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param updatedServizioJSON new JSON for the Servizio
	 * @param collectionName Collection where the Servizio is to be found
	 * @throws DBOperationFailedException
	 * 
	 */
	public void updateServizioByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String updatedServizioJSON, String collectionName) throws DBOperationFailedException {
		String method_name = "::updateServizioByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureUpdate = Patterns.ask(dbManager, 
													new UpdateServizioByID(client.path().name(), clientID, servizioID, updatedServizioJSON, collectionName), 
													1000);
		try {
			Reply replyUpdate = (Reply) Await.result(futureUpdate, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyUpdate instanceof UpdateServizioByIDSuccess || replyUpdate instanceof UpdateServizioByIDEnrichedSuccess) {
				logger.trace(method_name + "Updated servizio " + servizioID + " from collection " + collectionName);
			} else if (replyUpdate instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyUpdate).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyUpdate.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to update servizio " + servizioID + " from collection " + collectionName
									 + ": " + e.getMessage());
		}
	}

	
	 /** Update a Servizio with new Data
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param updatedServizioEnrichedJSON new Enriched JSON for the Servizio
	 * @param collectionName Collection where the Servizio is to be found
	 * @throws DBOperationFailedException
	 * 
	 */
	public void updateServizioEnrichedByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, TommyEnrichedJSON updatedServizioEnrichedJSON, String collectionName) throws DBOperationFailedException {
		String method_name = "::updateServizioEnrichedByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureUpdate = Patterns.ask(dbManager, 
													new UpdateServizioEnrichedByID(client.path().name(), clientID, servizioID, updatedServizioEnrichedJSON, collectionName), 
													1000);
		try {
			Reply replyUpdate = (Reply) Await.result(futureUpdate, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyUpdate instanceof UpdateServizioByIDEnrichedSuccess) {
				logger.trace(method_name + "Updated servizio " + servizioID + " in collection " + collectionName + " as plain JSON and not Enriched!");
			} else if (replyUpdate instanceof UpdateServizioByIDSuccess) {
				logger.trace(method_name + "Updated servizio " + servizioID + " in collection " + collectionName);
			} else if (replyUpdate instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyUpdate).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyUpdate.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to update servizio " + servizioID + " in collection " + collectionName
									 + ": " + e.getMessage());
		}
	}

	
	/**
	 * Write a new Servizio to the given Collection
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param newServizioJSON new JSON for the Servizio
	 * @param collectionName Collection where the Servizio is to be found
	 * @throws DBOperationFailedException
	 * 
	 */
	public void writeNewServizioByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, String newServizioJSON, String collectionName) throws DBOperationFailedException {
		String method_name = "::writeNewServizioByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureWrite = Patterns.ask(dbManager, 
												new WriteNewServizioByID(client.path().name(), clientID, servizioID, newServizioJSON, collectionName), 
												1000);
		try {
			Reply replyWrite = (Reply) Await.result(futureWrite, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyWrite instanceof WriteNewServizioByIDSuccess || replyWrite instanceof WriteNewServizioByIDSuccess) {
				logger.trace(method_name + "Wrote new servizio " + servizioID + " from collection " + collectionName);
			} else if (replyWrite instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyWrite).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyWrite.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to write new servizio " + servizioID + " from collection " + collectionName
									 + ": " + e.getMessage());
		}
	}
	
	

	/**
	 * Write a new Servizio to the given Collection
	 * @param client ActorRef of the client, caller of the Query
	 * @param dbManager ActorRef of the DB Manager Actor
	 * @param operationTimeOutSecs Timeout for the Ask Operation
	 * @param servizioID ID of the Servizio to move
	 * @param newServizioEnrichedJSON new Enriched JSON for the Servizio
	 * @param collectionName Collection where the Servizio is to be found
	 * @throws DBOperationFailedException
	 * 
	 */
	public void writeNewServizioEnrichedByID(ActorRef client, String clientID, ActorRef dbManager, int operationTimeOutSecs, String servizioID, TommyEnrichedJSON newServizioEnrichedJSON, String collectionName) throws DBOperationFailedException {
		String method_name = "::writeNewServizioEnrichedByID(): ";
		// Check Input Parameters
		if (operationTimeOutSecs < 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was negative");
		}
		if (operationTimeOutSecs == 0) {
			throw new DBOperationFailedException("Received Operation TimeOut was zero");
		}
		Future<Object> futureWrite = Patterns.ask(dbManager, 
												new WriteNewServizioEnrichedByID(client.path().name(), clientID, servizioID, newServizioEnrichedJSON, collectionName), 
												1000);
		try {
			Reply replyWrite = (Reply) Await.result(futureWrite, Duration.create(operationTimeOutSecs, TimeUnit.SECONDS));
			if (replyWrite instanceof WriteNewServizioByIDEnrichedSuccess) {
				logger.trace(method_name + "Wrote new servizio enriched " + servizioID + " to collection " + collectionName);
			} else if (replyWrite instanceof WriteNewServizioByIDSuccess) {
				logger.warn(method_name + "Wrote new servizio " + servizioID + " to collection " + collectionName + " as plain JSON and not Enriched!");
			} else if (replyWrite instanceof DBOperationFaillure) {
				throw new DBOperationFailedException(((DBOperationFaillure) replyWrite).getCause());
			} else {
				throw new DBOperationFailedException("Received unhandled reply of type: " + replyWrite.getReplyTypeName());
			}
		} catch (TimeoutException | InterruptedException | IllegalArgumentException e) {
			logger.error(method_name + " Failed to write new servizio " + servizioID + " to collection " + collectionName
									 + ": " + e.getMessage());
		}
	}

}
