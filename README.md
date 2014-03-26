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
* Combine hits using multiple different fields.

This highlighter does not (currently):
* Respect phrase mathces at all (all phrases are reduced to terms)
* Support require_field_match

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

Elasticsearch options
---------------------
The ```fragmenter``` field to defaults to ```scan``` but can also be set to
```sentence```.  ```sentence``` produces results that look like the Postings
Highlighter.  ```scan``` produces results that look like the Fast Vectory
Highlighter.  Example:
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "expiremental",
        "fragmenter": "sentence"
      }
    }
  }
```

The ```default_similarity``` option defaults to true for queries with more then
one term.  It will weigh each matched term using Lucene's default similarity
model similarly to how the Fast Vectory Highlighter weighs terms.  If can be
set to false to leave out that weighing.  If there is only a single term in the
query it will never be used.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "expiremental",
        "options": {
          "default_similarity": false
        }
      }
    }
  }
```

The ```hit_source``` option can force detecting matched terms from a particular
source.  It can be either ```postings```, ```vectors```, or ```analyze```.  If
set to ```postings``` but the field isn't indexed with ```index_options``` set
to ```offsets``` or set to ```vectors``` but ```term_vector``` isn't set to
```positions_offsets``` then the highlight throw back an error.  Defaults to
using the first option that wouldn't throw an error.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "expiremental",
        "options": {
          "hit_source": "analyze"
        }
      }
    }
  }
```

Elasticsearch matched_field support
-----------------------------------
This highlighter supports ```matched_fields``` just like the Fast Vectory
Highlighter.  See the [documentation](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/search-request-highlighting.html#matched-fields)
for more on it.  The only real difference is that each fields ```hit_source```
is determined independently.  If one field is short feel free to leave out any
special settings for ```index_options``` or ```term_vector```.


Offsets in postings or term vectors
-----------------------------------
Since adding offsets to the postings (set ```index_options``` to ```offsets```
in Elasticsearch) and creating term vectors with offsets (set ```term_vector```
to ```positions_offsets``` in Elasticsearch) both act to speed up highligting
of this highlighter you have a choice which to use.  Unless you have a
compelling reason go with adding offsets to the postings.  That is faster (by
my tests) and uses much less space.
