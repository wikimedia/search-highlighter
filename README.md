Experimental Highlighter [![Build Status](https://travis-ci.org/wikimedia/search-highlighter.svg?branch=master)](https://travis-ci.org/wikimedia/search-highlighter)
========================

Text highlighter for Java designed to be pluggable enough for easy
experimentation.  The idea being that it should be possible to play with how
hits are weighed or how they are grouped into snippets without knowing about
the guts of Lucene or Elasticsearch.

Comes in three flavors:
* Core: No dependencies jar containing most of the interesting logic
* Lucene: A jar containing a bridge between the core and lucene
* Elasticsearch: An Elasticsearch plugin

You can read more on how it works [here](docs/how_it_works.md).


Elasticsearch value proposition
-------------------------------
This highlighter
* Doesn't need offsets in postings or term enums with offsets but can use
either to speed itself up.
* Can fragment like the Postings Highlighter, the Fast Vector Highlighter,
or it can highlight the entire field.
* Can combine hits using multiple different fields (aka ```matched_fields```
support).
* Can boost matches that appear early in the document.
* By default boosts matches on unique query terms per fragment

This highlighter does not (currently):
* Support require_field_match

Elasticsearch installation
--------------------------

| Experimental Highlighter Plugin |  ElasticSearch  |
|---------------------------------|-----------------|
| 0.0.10 -> master                | 1.2.0 -> master |
| 0.0.9                           | 1.1.0           |

Install it like so:
```bash
./bin/plugin --install org.wikimedia.search.highlighter/experimental-highlighter-elasticsearch-plugin/0.0.10
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
        "type": "experimental"
      }
    }
  }
}
```

Elasticsearch options
---------------------
The ```fragmenter``` field to defaults to ```scan``` but can also be set to
```sentence``` or ```none```.  ```scan``` produces results that look like the
Fast Vector Highlighter.  ```sentence``` produces results that look like the
Postings Highlighter.  ```none``` won't fragment on anything so it is cleaner
if you have to highlight the whole field.  Multi-valued fields will always
fragment between each value, even on ```none```.  Example:
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "experimental",
        "fragmenter": "sentence",
        "options": {
          "locale": "en_us"
        }
      }
    }
  }
```
If using the ```sentence``` fragmenter you should specify the locale used for
sentence rules with the ```locale``` option as above.

Each fragmenter has different ```no_match_size``` strategies based on the
spirit of the fragmenter.

By default fragments are weighed such that additional matches for the same
query term are worth less then unique matched query terms.  This can be
customized with the ```fragment_weigher``` option.  Setting it to ```sum```
will weight a fragment as the sum of all its matches, just like the FVH.  The
default settings, ```exponential``` weighs fragments as the sum of:
 (base ^ match_count) * average_score
where match_count is the number of matches for that query term, average_score
is the average of the score of each of those matches, and base is a free
parameter that defaults to ```1.1```.  The default value of base is what
provides the discount on duplicate terms.  It can be changed by setting
```fragment_weigher``` like this: ```{"exponential": {"base": 1.01}}```.
Setting the ```base``` closer to ```1``` will make duplicate matches worth
less.   Setting the ```base``` between ```0``` and ```1``` will make duplicate
matches worth less then single matches which doesn't make much sense (but is
possible.)  Similarly, setting ```base``` to a negative number or a number
greater then ```sqrt(2)``` will do other probably less then desireable things.

The ```top_scoring``` option can be set to true while sorting fragments by
source to return only the top scoring fragmenter but leave them in source
order.  Example:
```js
  "highlight": {
    "fields": {
      "text": {
        "type": "experimental",
        "number_of_fragments": 2,
        "fragmenter": "sentence",
        "sort": "source",
        "options": {
           "locale": "en_us",
           "top_scoring": true
        }
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
        "type": "experimental",
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
```with_positions_offsets``` then the highlight throw back an error.  Defaults
to using the first option that wouldn't throw an error.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "experimental",
        "options": {
          "hit_source": "analyze"
        }
      }
    }
  }
```

The ```boost_before``` option lets you set up boosts before positions.  For
example, this will multiply the weight of matches before the 20th position by
5 abd before the 100th position by 1.5.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "experimental",
        "order": "score",
        "options": {
          "boost_before": {
            "20": 5,
            "100": 1.5
          }
        }
      }
    }
  }
```
Note that the position is not reset between multiple values of the same field
but is handled independently for each of the ```matched_fields```.
Note also that ```boost_before``` works with ```top_scoring```.

The ```max_fragments_scored``` option lets you limit the number of fragments
scored.  The default is Integer.MAX_VALUE so you'll score them all.  This can
be used to limit the CPU cost of scoring many matches when it is likely that
the first few matches will have the highest score.  This is quite likely the
configuration also contains ```boost_before```.

The ```matched_fields``` field turns on combining matches from multiple fields,
just like the Fast Vector Highlighter.  See the [Elasticsearch documentation](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/search-request-highlighting.html#matched-fields)
for more on it.  The only real difference is that if ```hit_source``` is left
out then each field's HitSource is determined independently if .  If one field
is short feel free to leave out any special settings for ```index_options``` or
for ```term_vector```s.

A limitation in ```matched_fields```: if the highlighlighter has to analyze the
field value to find hits then you can't reuse analyzers in each matched field.

The ```fetch_fields``` option can be used to return fields next to the
highlighted field.  It is designed for use with object fields but has a number
of limitations.  Read more about it [here](docs/fetched_fields.md).

The ```phrase_as_terms``` option can be set to true to highlight phrase queries
(and multi phrase prefix queries) as a set of terms rather then a phrase.  This
defaults to ```false``` so phrase queries are restricted to full phrase
matches.

The ```regex``` option lets you set regular expressions that identify hits. It
can be specified as a string for a single regular expression or a list for
more then one.  You ```regex_flavor``` option sets the flavor of regex.  The
default flavor is ```[lucene](https://lucene.apache.org/core/4_9_0/core/org/apache/lucene/util/automaton/RegExp.html)```
and the other option is ```[java](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)```.
Its also possible to skip matching the query entirely by setting the
```skip_query``` option to ```true```.  The ```regex_case_insensitive``` option
can be set to true to make the regex case insensitive using the case rules in
the locale specified by ```locale```.  Example:
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "experimental",
        "options": {
          "regex": [
            "fo+",
            "bar|z",
            "bor?t blah"
          ],
          "regex_flavor": "lucene",
          "skip_query": true,
          "locale": "en_US",
          "regex_case_insensitive":
        }
      }
    }
  }
```

Offsets in postings or term vectors
-----------------------------------
Since adding offsets to the postings (set ```index_options``` to ```offsets```
in Elasticsearch) and creating term vectors with offsets (set ```term_vector```
to ```with_positions_offsets``` in Elasticsearch) both act to speed up
highligting of this highlighter you have a choice which to use.  Unless you
have a compelling reason go with adding offsets to the postings.  That is
faster (by my tests, at least) and uses much less space.
