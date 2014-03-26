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


Elasticsearch value proposition
-------------------------------
This highlighter can
* Generate hits by reanalyzing the string, loading offset from postings, or
loading offsets from term vectors.  It produces the same output regardless of
which method it uses.
* Fragment either by scanning for characters (like the FVH does) or by
delegating to Java's BreakIterator (like the Postings Highlighter).  Scanning
is noticably faster in some cases.

This highlighter does not (currently):
* Respect phrase mathces at all (all phrases are reduced to terms)

Elasticsearch installation
--------------------------

| Expiremental Highlighter Plugin |  ElasticSearch  |
|---------------------------------|-----------------|
| master                          | 1.0.1 -> master |

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
