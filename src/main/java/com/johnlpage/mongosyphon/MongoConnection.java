package com.johnlpage.mongosyphon;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;


public class MongoConnection {
	JobDescription target;
	static final int BATCHSIZE=100;
	Logger logger;	
	MongoClient mongoClient=null;
	MongoDatabase db = null;
	MongoCollection<Document>  collection = null;
	List<WriteModel<Document>> ops;
	int nops =0;
	
	public MongoConnection(JobDescription target)
	{
		logger = LoggerFactory.getLogger(MongoSyphon.class);
		this.target = target;
		logger.info("Connecting to " + target.getMongoURI() );
		mongoClient = new MongoClient(new MongoClientURI(target.getMongoURI()));
		db = mongoClient.getDatabase(target.getMongoDatabase());
		collection = db.getCollection(target.getMongoCollection());
		
		ops = new ArrayList<WriteModel<Document>>();
	}
	
	public Document FindOne(Document query,Document fields,Document order)
	{
		Document rval = null;
		
		if(query == null) { query = new Document();}
		
		FindIterable<Document> fi = collection.find(query,Document.class);
	
		if(fields != null) {	logger.info("project:" + fields.toJson());;
		fi.projection(fields);}
		if(order != null) { fi.sort(order);}
		MongoCursor<Document> c = fi.iterator();
		if(c.hasNext()) { rval=c.next();}
		return rval;
		
	}
	
	//Document shoudl have a 'find' field
	public void Update(Document doc, boolean upsert)
	{
		Document find;
		UpdateOptions uo = new UpdateOptions();
		uo.upsert(upsert);
		if(doc.containsKey("$find")) {
			find = (Document)doc.get("$find");
			doc.remove("$find");
		
			ops.add(new UpdateOneModel<Document>(find,doc,uo));
		} else {
			logger.error("No $find section defined");
			System.exit(1);
		}
		FlushOpsIfFull();
	}
	
	private void FlushOpsIfFull()
	{
		boolean fatalerror = false;
		boolean success = false;
		if(ops.size() > MongoConnection.BATCHSIZE )
		{
			try {
				collection.bulkWrite(ops);
			}  catch (com.mongodb.MongoBulkWriteException err) {
				//  Duplicate inserts are not an error if retrying
				for (BulkWriteError bwerror : err.getWriteErrors()) {
		
					if (bwerror
							.getCategory() != ErrorCategory.DUPLICATE_KEY) {
						logger.error(bwerror.getMessage());
						fatalerror = true;
						break;
					}
				}
				if (!fatalerror) {
					//Only dups so we are done
					success = true;
				}
			} catch (MongoException err) {
				// This is some other type of error not a BulkWriteError
				// object just sleep for 3 seconds and retry - this covers network Outages, elections etc.
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					logger.error(e.getMessage());
					fatalerror = true;
				}
				logger.error("Error: " + err.getMessage());
			}
			ops.clear();
		}
	}
	//Maps to batched inserts
	
	public void Create(Document doc)
	{
		
		ops.add(new  InsertOneModel<Document>(doc));
		FlushOpsIfFull();
	}
	
	//Add updates here
	
	public void close()
	{
		if(ops.size()>0 ){
			collection.bulkWrite(ops);
			ops.clear();
		}
	}
}
