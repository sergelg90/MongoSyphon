# MongoSyphon

## Introduction

MongoSyphon is an Extract/Transform/Load (ETL) Engine designed specifically to transform and transfer data from Relational databases such as MySQL, Postgres and Oracle to document structures in MongoDB.

It has been designed specifically to target MongoDB and to make use of the powerful update facilities it provides. This differs from many other ETL tools which  target relational structures and have a minimal MongoDB add-on. MongoSyphon can be used both for an initial bulk transfer and for ongoing updates.

MongoSyphon does not contain an explicit Change Data Capture (CDC) capability but is able to perform basic CDC tasks through SQL querying or make use of change tables generated by an external CDC.

It required that users are competent in SQL and know the structure of the source data. It is also assumed they can make a judgement and/or measure the impact on the source system when running MongoSyphon.

It is also assumed that the user is able to understand and define the MongoDB target collection.

A core tenet of MongoSyphon is to push work to either the source or to MongoDB wherever possible. The engine itself is intended to me small, lightweight and fast without huge memory requirements.

There is no GUI for MongoSyphon, scheduling should be via cron or similar for ongoing processes.


## Short Demo
### Build
1. Checkout from Github
2. mvn package

### Prerequisites

1. MongoDB on port 27017 with auth to write (root/password)
2. MySQl on port 3306 with auth to write (root/password)
3. mongo client and mysql in your path

### Demo

First make a sample database in your RDBMS (MySQL)

```
> cd example
> cat mkpets.sql

drop database if exists sdemo;
create database sdemo;
use sdemo;

create table owner ( ownerid int primary key, name VARCHAR(20), address VARCHAR(60));
CREATE TABLE species ( speciesid int primary key ,  species VARCHAR(20));
CREATE TABLE pet ( petid int primary key, name VARCHAR(20), owner int, species int);

insert into species values(1,"Dog");
insert into species values(2,"Cat");

insert into owner values (1,"John","22 Accacia Avenue");
insert into owner values (2,"Sarah","19 Main Street");

insert into pet values (1,"Brea",1,1);
insert into pet values (2,"Harvest",1,1);
insert into pet values (3,"Mittens",2,2);


> mysql -uroot -ppassword < mkpets.sql
> mysql -u root -ppassword
mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 595
Server version: 5.7.17 MySQL Community Server (GPL)

Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> describe sdemo;
ERROR 1046 (3D000): No database selected
mysql> use sdemo;
Reading table information for completion of table and column names
You can turn off this feature to get a quicker startup with -A

Database changed
mysql> show tables;
+-----------------+
| Tables_in_sdemo |
+-----------------+
| owner           |
| pet             |
| species         |
+-----------------+
3 rows in set (0.00 sec)

mysql> exit
```

Now we have a three table schema each _owner_ has 0 or more pets, each _pet_ has exactly one _species_ from a small list.

```
>cat owners.js
{
	databaseConnection: "jdbc:mysql://localhost:3306/sdemo?useSSL=false",
	databaseUser: "root",
	databasePassword: "password",
	mongoConnection: "mongodb://localhost:27017/",
	mongoDatabase: "sdemo",
	mongoCollection: "owners",

	startAt: "ownerssection",


	ownerssection: {
		template: {
			_id: "$ownerid",
			name: "$name",
			address : "$address",
			pets : [ "@petsection" ]
		},
		sql: 'SELECT * FROM owner',

	},


	petsection: {
		template: {
			petid: "$petid",
			name: "$name",
			species : "@speciessection"
		},
		sql: 'SELECT * FROM pet where owner = ?',
		params: [ "ownerid" ]

	},

	speciessection: {
		template: {
			_value : "$species"
		},
		sql: 'SELECT * from species where speciesid = ?',
		params : [ "species" ]

	}

}
>java -jar ../bin/MongoSyphon.jar -c owners.js 
2 records converted in 0 seconds at an average of 7 records/s
>mongo -uroot -ppassword
> use sdemo
switched to db sdemo
> db.owners.find().pretty()
{
	"_id" : 1,
	"name" : "John",
	"address" : "22 Accacia Avenue",
	"pets" : [
		{
			"petid" : 1,
			"name" : "Brea",
			"species" : "Dog"
		},
		{
			"petid" : 2,
			"name" : "Harvest",
			"species" : "Dog"
		}
	]
}
{
	"_id" : 2,
	"name" : "Sarah",
	"address" : "19 Main Street",
	"pets" : [
		{
			"petid" : 3,
			"name" : "Mittens",
			"species" : "Cat"
		}
	]
}
>exit
```



## WARNING Version 0.0.1

**MongoSyphon is an alpha stage, open-source tool and has the following characteristics**

* Not well enough tested
* Limited Error Handling
* At-will support from the Author

These will improve over time but usage is **at your own risk** and there are no implied warranties or promises it won't eat your data or lie to you.

Please report any bugs of issues you find.

This is released with an Apache 2.0 license.

## Command line
```
Usage: java -jar MongoSyphon.jar [args]

Args:
   -c <config file>
   -h <help>
   -n <new output config>
```

## Config Files

### Nature

MongoSyphon is driven from JSON format configuration files. Early prototypes used YAML configuration files however this resulted in embedding JSON in YAML and was ultimately less readable. This also lends itself to storing the ETL configurations inside MongoDB itself if desired.

Each configuraton file defines a complete ETL process. At this time that is either an insert, update or upsert into a single MongoDB collection with documents generated from one or more relational tables.

In future parallel generation of codepentant records into multiple collections to generate more 'relational' data in MongoDB may also be supported.


### Config Format

#### Connection Details
Each config file minimally includes, as top level elements, the connection details for the RDBMS and for MongoDB. Connection to the RDBMS is via JDBC and a suitable JDBC Driver must be in your classpath. JDBC drivers for MySQL and Prostgres are included in the JAR and the Maven build file.

The connection part of a config looks like this:

```
{
"databaseConnection": "jdbc:mysql://localhost:3306/jmdb?useSSL=false",
"databaseUser": "someuser",
"databasePassword": "somepassword",
"mongoConnection": "mongodb://SomeHost.local:27017/",
"mongoDatabase": "imdb",
"mongoCollection": "movies"

...

}
```

The full range of Connection string options are available for MongoDB and the RDBMS. To use username/password authentication for MongoDB it should be included in the URI [Mongo URI Format](https://docs.mongodb.com/manual/reference/connection-string/). In future additional password options may be included to avoid passwords in configuration files when using passworded login.

|Option|Description|Mandatory|
|------|-----------|---------|
| databaseConnection| A JDBC Connection string to the RDBMS of your choice, you must have an appropriate driver jar in your classpath, mysql and postgres build with MongoSyphone and are in it's JAR|Yes|
|databaseUser| A user who can log in and read from the RDBMS| Yes |
|databasePassword|A password for the user above | Yes |
|mongoConnection| A MongoDB URI to connect to MongoDB. Any options like users, security, write concerns should go here.| Yes|
|mongoDatabase | The MongoDB Database that is being written to | Yes|
|mongoCollection | The MongoDB Collection that is being wrtitten to | Yes |


#### Start Point and Sections

The rest of the config file is divided into names sections with one section being the _first_ or _top_ section. A section defines a SQL Query, a Template, a Parameters section if required and any caching/execution options.

A simple section might look like this

```
	startAt: "ownerssection",


	ownerssection: {
		template: {
			_id: "$ownerid",
			name: "$name",
			address : "$address",
		},
		sql: 'SELECT * FROM owner'
	}
```

`startAt: "ownerssection"` is used to denote the top level section as the starting point for an ETL process the section here woudl normally also define the _id field is one is being mapped from the RDBMS.

The section itself consist of a SQL query `sql:` and a template `template:`

The simple description of the logic here is.

* Run the SQL Query
* For Each Row in Results:
	- Apply the Row values to the template to make a Document.
	- Insert that document in the MongoDB collection.
	- `$name` means the contents of a column called name.
	- without the $ the explicit value is included.
	- Null or empty columns are not included.


#### Nested Sections

The power of MongSyphon is in it's ability to build nested document structures - to do this us uses the concept of nested sections. When evaluating a section, MongoSyphone can evaluate the contents of another section which may return one or more documented and embed them in the higher level section. This is specified using the `@section` notation.

Taking our Pet Owners example.

```
	startAt: "ownerssection",


	ownerssection: {
		template: {
			_id: "$ownerid",
			name: "$name",
			address : "$address",
			pets : [ "@petsection" ]
		},
		sql: 'SELECT * FROM owner'
	},


	petsection: {
		template: {
			petid: "$petid",
			name: "$name",
		},
		sql: 'SELECT * FROM pet where owner = ?',
		params: [ "ownerid" ]

	}
```

In `ownerssection` we specify that for each owner we want an Array (denoted by the square brackets) of results of `petsection`. If the array is empty the field will be omitteed rather than embed an empty array.

`petsection` is similar to `ownersection` except the SQL uses a WHERE clause with SQL placehoders _?_ in the query to retrieve a subset of the data. In this case oly pets for a given owner. The values used to parameterise the query are defined in the `params:` value which is always an array of column names from the parent or calling section. In this case ownerid from ownersection.

***It is Critical that these queries are correctly indexed on the RDBMS***

#### Single Embedded Objects

Calling a sub section with the parameter surrounded by square brackets embeds an array in the parent. There are also two ways to create a nested object rather than array.

The simplest is used to take values from the current query and present them nested. Imagine we had an RDBMS Row of the form

|Name|StreetNumber|StreetName|Town|Country|PostalCode|
|----|------------|----------|----|-------|----------|
|Charlotte|22|Accacia Avenue|Staines|UK|ST1 8BP|

We could have a template like this.

```
 peoplesection: {
        template: {
            name: "$name",
            streetnumber: "$streetnumber",
            streetname: "$streetname",
            town: "$town",
            country: "$country",
            postalcode: "$postalcode"
        },
        sql: 'SELECT * FROM people'
    }
```

Or we may want a more nested schema from that single row - like this


```
 peoplesection: {
        template: {
	            name: "$name",
	            address : {
		            streetnumber: "$streetnumber",
		            streetname: "$streetname",
		            town: "$town",
		            country: "$country",
		            postalcode: "$postalcode"
            }
        },
        sql: 'SELECT * FROM people'
    }
```

Which is also valid.


We can also embed an object from a sub-section by calling the subsection but _not including the square brackets_ this causes the subsection to be evaluated _only for the first matching value_. In our pets example we may do this for a lookup table like the pet's species like so. This shoudl only be used normally where you *know* there is only a single matching value.


```

	petsection: {
		template: {
			petid: "$petid",
			name: "$name",
			type : "@speciessection"
		},
		sql: 'SELECT * FROM pet where owner = ?',
		params: [ "ownerid" ]

	},

	speciessection: {
		template: {
			species : "$species",
			breed : "$breed"
		},
		sql: 'SELECT * from species where speciesid = ?',
		params : [ "species" ]

	}
```
	
This would result in a document like:

```
{
	"_id" : 2,
	"name" : "Sarah",
	"address" : "19 Main Street",
	"pets" : [
		{
			"petid" : 3,
			"name" : "Mittens",
			"type" : { species:  "Cat",
			           breed : "Siamese"
			         }
		}
	]
}
```

#### Scalar Values

Sometimes you have only a single value you are interested in being retiurned from a sub-section. This can be the case regardless of whther it is being returned to an array or a scalar at a higher level - you don't want to return an object as it will have a single member.

Mongosyphon lets us use the special value `_value` to denote that the output of this section shoudl not be an object but in fact a scalar.

This is often the case when the section relates to a lookup table. If, in our example above, we did not have _species_ *and* _breed_ for each pet but just the species then we may choose to define it defferently. By putting the species values into `_value` we can get a different output.



```

	petsection: {
		template: {
			petid: "$petid",
			name: "$name",
			type : "@speciessection"
		},
		sql: 'SELECT * FROM pet where owner = ?',
		params: [ "ownerid" ]

	},

	speciessection: {
		template: {
			_value : "$species",
		},
		sql: 'SELECT * from species where speciesid = ?',
		params : [ "species" ]

	}
```

We get the output


```
{
	"_id" : 2,
	"name" : "Sarah",
	"address" : "19 Main Street",
	"pets" : [
		{
			"petid" : 3,
			"name" : "Mittens",
			"type" : "Cat"
		}
	]
}
```

Note that _type_ is not a String value not an object.

### Caching Sections

The default mode in mongosyphone is to perform standalone SQL queries (using pre-prepared statements and pooled connections for efficiency) to retrieve each lower section. In some cases this can result in the same query being performed many times - for example retrieving the species = cat data in the above example for each cat in the database.

Most RDBMS will cache frequent queries like this but there is still considerable overhead in round trips to the server, preparing the query etc. Mongosyphone allos you to cache a section at the client side if it is expected to return a relatively small number of discrete results. For example we know there are only a few pet species so we can add the parameter `cached: true` in the section. Using this will cause MongoSyphon to cache the output for each set of input params and greatly reduce calls to the server.

Be aware that you can cache any size of sub or nested object but the cache will require RAM and will grow with each new entry there is no cache eviction.

### Merging Sections

In many mappings from RDBMS to MongoDB there is a one to many relationship between one table an another - in our example each owner has many pets. Each pet has a single owner. In this case we can use a more efficient method to generate the data by sorting and merging the source tables. this is the option `mergeon`.

To do this we need to ensure both sections are sorted by the field we are using to join them. 'ownerid' in the owners tables and 'owner' in the pets table.

We can then use 'mergeon' to walk both tables merging the results.

_behind the scenes, MongoSyphon simply keeps the cursor open for the sub document and assumes any results it is looking for will be odered at the point it previously left off_

Our pet's config, using this efficient merging mechnism, which avoids a query on the pets table per owner looks like this.

```
	ownerssection: {
		template: {
			_id: "$ownerid",
			name: "$name",
			address : "$address",
			pets : [ "@petsection" ]
		},
		sql: 'SELECT *,ownerid as owner FROM owner order by ownerid',

	},


	petsection: {
		template: {
			petid: "$petid",
			name: "$name",
		},
		sql: 'SELECT * FROM pet order by owner',
		mergeon:  "owner" 

	},

```

Currently MongoSyphone only merges on a field with the same name, therefore we need to alias the field ownerid to owner using SQL. In future this will allow merging on multiple columns with different names.

This typically requires you have an index on the column being sorted however that would also be required for the query based method above and woudl typically exist for retrieval anyway.

### Pushing transformation work  to SQL

All the previous examples have used simple SQL statements against a single database. The reason the JOIN operations have not been pushed ot the underlying database is that there is rarely a good and efficient way to do that for a 1 to Many relationship. You either have to retrieve repeated columns for many rows or use somethign like MySQL's **GROUP_CONCAT** function and then parse a text string.

For Many to Many and One to One relationships, sometimes it is better to push some logic into the SQL query and MongoSyphon imploses no limits on this other than your own SQL abilities.

We have seen an example of renaming a field above using *AS* where we wanted a column with a different name,

`sql: 'SELECT *,ownerid as owner FROM owner order by ownerid'`

We might also want to perform an operation like a sum or concatenation at the RDBMS side.

`sql: 'SELECT *,LEFT(telno,3) AS AREACODE,MID(telno,3,7) as DIGITS'`

Finally we can use the RDBMS to perform a server side **JOIN** where it is appropriate using any SQL syntax. Again correct indexing is vital to the performance of this but it *may* still be faster than using MongoSyphon to perform the join.

```
sql: 'SELECT ACTOR.NAME as NAME ,ACTORS2MOVIES.ROLE AS ROLE
		 FROM ACTORS,ACTORS2MOVIES WHERE MOVIEID=? AND 
		 ACTORS.ACTORID=ACTORS2MOVIES.ACTORID'
```

### Updates and Inserts

All previousl examples have used the implied defaul `mode` of insert. Where no `mode:` parameter is supplied MonngoSyphon will attempt to insert all records it generates.

It is possible to use MongoSyphone to update an existing MongoDB database as well to so this use `mode: "update"` or `mode: "upsert"` as top level parameters depending on the behaviour you desire where no match is found.

There is no 'replace/save' mode currently in MongoSyphon although this is being considered for the next version.

When using either updating mode the format of the top level document is different it must be of the form:

```
someupdatesection: {
	template: {
	  "$find" : { ... },
	  "$set" : { ... },
	  "$inc" : { ... },
	  "$push" : {...}
	},
sql: 'select X from Y'
}
```
The template should correspond to a MongoDB update document specificatioin using one of more update operators. The exception is the "$find" operator which specifies which *single* document to find an update. Currently multi-update is not supported.

By using the $ operator you can create update specifications which query an RDBMS table and then push updates into nested documents and arrays in MongoDB. For example if we now had a collection mapping pet_id's to microchip numbers we could push those through to update the pet records, or push them unto an array under the owner.

This is a very powerful feature but will take some thinking about to gain maximum benefit.


### Reading from MongoDB

Normally the top level section is not in any way parameterised however after an inial import it is possible you wish to only take new or modified records versus the MongoDB destination.

One way to do this is with an external CDC tool which creates additional RDBMS collections with those records added, updated of deleted. This is the correct and most comprehensive way of managing change althogh it still needs mapping to the Mongo structure, where a change to one row in the source may impact many documents.

MongoSyphone also includes the ability to query MongoDB at the start for a single record and use that to parameterise the top level section. As a very simple example. Assume you are converting 10 Million records - they will be streamed from the RDBMS to MongoDB by MongoSyphon.

This stops part way through for some reason and you have N million converted.

As long as when you converted them you were sorting by some useful key, perhaps the primary key. With eh Mongo Query facility you can query mongodb for the highest primary key it has loaded and continue from there. This makes it restartable but also allows for ongoing migration where you have an incrementing serial number of datestamped changes.

The MongoDB Query is run against the top level collection you are loading, it returns a single Document which is treated like an RDBMS row. It is configured using three top level paramaeters.

The examples below simply mean, find the highest _id value in the Mongo Database

|Parameter|Meaning|
|--------|---|
|mongoQuery|Any MongoDB QUery to run against the source DB|
|mongoFields|Any MongoDB Projection to limit the returned set of fields|
|mongoOrderBy|Any MongoDB ordering crieteria critical when only a single record is being returned|
|mongoDefault|The object to return where the query fonds no records, for example on an empty DB|

Example, assuming in the source *movieid* is a sequence, find any new movies. So query all movies, order by _id in reverse adn take the first one. If nothing found then return an _id value of -1


```
"mongoDatabase": "imdb",
"mongoCollection": "movies",
"mongoQuery" : {},
"mongoFields" : { "_id" : 1},
"mongoOrderBy" : { "_id" : -1},
"mongoDefault" : { "_id" : 0 },
"startAt": "moviessection",

"moviessection": {
    "template": {
      "_id": "$movieid",
      "title": "$title",
      "year": "$year",
       },
    "sql": "SELECT * FROM movies where movieid > ? order by movieid",
    "params" : [ "_id" ]
  },
```


*Support for querying a different collection to support MongoDB 3.4 Views and thereforeAggregations will be added at a future point.*

Using this currently may require some MongoDB schema changes to support it - for example:

Given our owners and pets scenario - new pets are added to the RDBMS - to determine the last petid we saw we need to query MongoDB for the max petid which is in a subdocument.

To do this we can use an aggregation pipeline or a view built on one but that will not be fast.

In each document (owner) we can add a top level field (maxpetid) calculated using it's own section on initial load and using $max on subsequent updates. We can index and sort on this as we are doing above for _id. This is the current best approach. but needs another field and index to support it.

We can run an additional top level update to calculate and retain the max value of *petid* in a different collection , as long as we can specify a collection for the Mongo Query. This shoudl be bound into the same config though once multiple parallel conversion are allowed.

### New Config Generation

If you create a config file with only the connection parameters in it and supply it with `-c config` then using the `-n <outfile>` flag will generate a skeleton config with a section for each table in the database. This can save on transcription errors.


### Logging and output

Logging is handled by logback and configured in the logback xml either in the JAR file or supplied at runtime. By default logging data is verbose and goes to MongoSyphon.log in the directory you are running MongoSyphon.

