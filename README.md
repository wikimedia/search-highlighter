Expiremental Highlighter
========================

Text highlighter for Java designed to be pluggable enough for easy
expirementation.  The idea being that it should be possible to play with how
hits are weighed or how they are grouped into snippets without knowing about
the guts of Lucene or Elasticsearch.

Comes in three flavors:
* Core: No dependencies jar containing most of the interesting logic.
* Lucene: A jar containing a bridge between the core and lucene.
* Elasticsearch: An Elasticsearch plugin.



Elasticsearch installation
--------------------------

    ------------------------------------------------------
    | Expireemental Highlighter Plugin |  ElasticSearch  |
    ------------------------------------------------------
    | master                           | 1.0.1 -> master |
    ------------------------------------------------------

At this point nothing has been pushed to Elasticsearch's plugin repository so
you have to clone the plugin locally, build it by going to the cloned directory
and
```bash
mvn clean package
export ABSOLUTE_PATH_OF_CLONED_DIRECTORY=$(pwd)
```
then install by going to the root of the Elasticsearch installation and
```bash
./bin/plugin --url file:///$ABSOLUTE_PATH_OF_CLONED_DIRECTORY/expiremental-highlighter-elasticsearch-plugin/target/releases/expiremental-highlighter-elasticsearch-plugin-0.0.1-SNAPSHOT.zip  --install expiremental-highlighter-elasticsearch-plugin 
```

Then you can use it by searching like so:
```js
{
  "_source": false,
  "query": {
    "query_string": {
      "query": "hello world"
    }
  },
  "highlight": {
    "order": "score",
    "fields": {
      "title": {
        "number_of_fragments": 1,
        "type": "expiremental"
      }
    }
  }
}
```
