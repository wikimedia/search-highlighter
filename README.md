Cirrus Highlighter [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-highlighter)](https://integration.wikimedia.org/ci/job/search-highlighter/)
========================

Text highlighter for Java designed to be pluggable enough for easy
experimentation.  The idea being that it should be possible to play with how
hits are weighed or how they are grouped into snippets without knowing about
the guts of Lucene or OpenSearch.

Comes in three flavors:
* Core: No dependencies jar containing most of the interesting logic
* Lucene: A jar containing a bridge between the core and lucene
* OpenSearch: An OpenSearch plugin

You can read more on how it works [here](docs/how_it_works.md).


Value proposition
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

OpenSearch installation
-----------------------

| Cirrus Highlighter Plugin | OpenSearch |
| --------------------------|------------|
| 1.3.17                    | 1.3.17     |

Install it like so for OpenSearch 1.x.x:
```bash
./bin/opensearch-plugin install org.wikimedia.search.highlighter:cirrus-highlighter-opensearch-plugin:1.x.x
```

Elasticsearch installation
--------------------------

Prior to the migration to OpenSearch this plugin was known as the
experimental highlighter plugin.

| Experimental Highlighter Plugin |  Elasticsearch  |
|---------------------------------|-----------------|
| 7.10.0, master branch           | 7.10.0          |
| 7.5.1                           | 7.5.1           |
| 6.3.1.2                         | 6.3.1           |
| 5.5.2.2                         | 5.5.2           |
| 5.4.3                           | 5.4.3           |
| 5.3.2                           | 5.3.2           |
| 5.3.1                           | 5.3.1           |
| 5.3.0                           | 5.3.0           |
| 5.2.2                           | 5.2.2           |
| 5.2.1                           | 5.2.1           |
| 5.2.0                           | 5.2.0           |
| 5.1.2                           | 5.1.2           |
| 2.4.1                           | 2.4.1           |
| 2.4.0,                          | 2.4.0           |
| 2.3.5, 2.3 branch               | 2.3.5           |
| 2.3.4                           | 2.3.4           |
| 2.3.3                           | 2.3.3           |
| 2.2.2, 2.2 branch               | 2.2.2           |
| 2.1.2, 2.1 branch               | 2.1.2           |
| 2.0.2, 2.0 branch               | 2.0.2           |
| 1.7.0 -> 1.7.1, 1.7 branch      | 1.7.X           |
| 1.6.0, 1.6 branch               | 1.6.X           |
| 1.5.0 -> 1.5.1, 1.5 branch      | 1.5.X           |
| 1.4.0 -> 1.4.1, 1.4 branch      | 1.4.X           |
| 0.0.11 -> 1.3.0, 1.3 branch     | 1.3.X           |
| 0.0.10                          | 1.2.X           |
| 0.0.1 -> 0.0.9                  | 1.1.X           |

Install it like so for Elasticsearch 5.x.x:
```bash
./bin/elasticsearch-plugin install org.wikimedia.search.highlighter:experimental-highlighter-elasticsearch-plugin:5.x.x
```

Install it like so for Elasticsearch 2.x.x:
```bash
./bin/plugin install org.wikimedia.search.highlighter/experimental-highlighter-elasticsearch-plugin/2.x.x
```

Install it like so for Elasticsearch 1.7.x:
```bash
./bin/plugin --install org.wikimedia.search.highlighter/experimental-highlighter-elasticsearch-plugin/1.7.0
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
        "type": "cirrus"
      }
    }
  }
}
```

OpenSearch options
---------------------
The ```fragmenter``` field defaults to ```scan``` but can also be set to
```sentence``` or ```none```.  ```scan``` produces results that look like the
Fast Vector Highlighter.  ```sentence``` produces results that look like the
Postings Highlighter.  ```none``` won't fragment on anything so it is cleaner
if you have to highlight the whole field.  Multi-valued fields will always
fragment between each value, even on ```none```.  Example:
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "cirrus",
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
query term are worth less than unique matched query terms.  This can be
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
matches worth less than single matches which doesn't make much sense (but is
possible).  Similarly, setting ```base``` to a negative number or a number
greater then ```sqrt(2)``` will do other probably less than desirable things.

The ```top_scoring``` option can be set to true while sorting fragments by
source to return only the top scoring fragmenter but leave them in source
order.  Example:
```js
  "highlight": {
    "fields": {
      "text": {
        "type": "cirrus",
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

The ```default_similarity``` option defaults to true for queries with more than
one term.  It will weigh each matched term using Lucene's default similarity
model similarly to how the Fast Vector Highlighter weighs terms.  If can be
set to false to leave out that weighing.  If there is only a single term in the
query it will never be used.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "cirrus",
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
        "type": "cirrus",
        "options": {
          "hit_source": "analyze"
        }
      }
    }
  }
```

The ```boost_before``` option lets you set up boosts before positions.  For
example, this will multiply the weight of matches before the 20th position by
5 and before the 100th position by 1.5.
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "cirrus",
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
the first few matches will have the highest score.

The ```matched_fields``` field turns on combining matches from multiple fields,
just like the Fast Vector Highlighter.  See the [Elasticsearch documentation](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/search-request-highlighting.html#matched-fields)
for more on it.  The only real difference is that if ```hit_source``` is left
out then each field's HitSource is determined independently which isn't
possible with the fast vector highlighter as it only supports the
```postings``` hit source.  Remember: For very short fields ```analyze``` hit
source will be the most efficient because no secondary data has to be loaded
from disk.

A limitation in ```matched_fields```: if the highlighter has to analyze the
field value to find hits then you can't reuse analyzers in each matched field.

The ```fetch_fields``` option can be used to return fields next to the
highlighted field.  It is designed for use with object fields but has a number
of limitations.  Read more about it [here](docs/fetch_fields.md).

The ```phrase_as_terms``` option can be set to true to highlight phrase queries
(and multi phrase prefix queries) as a set of terms rather then a phrase.  This
defaults to ```false``` so phrase queries are restricted to full phrase
matches.

The ```regex``` option lets you set regular expressions that identify hits. It
can be specified as a string for a single regular expression or a list for
more than one.  Your ```regex_flavor``` option sets the flavor of regex.  The
default flavor is [lucene](https://lucene.apache.org/core/4_9_0/core/org/apache/lucene/util/automaton/RegExp.html)
and the other option is [java](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).
It's also possible to skip matching the query entirely by setting the
```skip_query``` option to ```true```.  The ```regex_case_insensitive``` option
can be set to true to make the regex case insensitive using the case rules in
the locale specified by ```locale```.  Example:
```js
  "highlight": {
    "fields": {
      "title": {
        "type": "cirrus",
        "options": {
          "regex": [
            "fo+",
            "bar|z",
            "bor?t blah"
          ],
          "regex_flavor": "lucene",
          "skip_query": true,
          "locale": "en_US",
          "regex_case_insensitive": true
        }
      }
    }
  }
```
If a regex match is wider than the allowed snippet size it won't be returned.

The ```max_determinized_states``` option can be used to limit the complexity
explosion that comes from compiling Lucene Regular Expressions into DFAs.  It
defaults to 20,000 states.  Increasing it allows more complex regexes to take
the memory and time that they need to compile.  The default allows for
reasonably complex regexes.

The ```skip_if_last_matched``` option can be used to entirely skip highlighting
if the last field matched.  This can be used to form "chains" of fields only one
of which will return a match:
```js
  "highlight": {
    "type": "cirrus",
    "fields": {
      "text": {},
      "aux_text": { "options": { "skip_if_last_matched": true } },
      "title": {},
      "redirect": { "options": { "skip_if_last_matched": true } },
      "section_heading": { "options": { "skip_if_last_matched": true } },
      "category": { "options": { "skip_if_last_matched": true } },
    }
  }
```
The above example creates two "chains":
* aux_text will only be highlighted if there isn't a match in text.
-and-
* redirect will only be highlighted if there isn't a match in title.
* section_heading will only be highlighted if there isn't a match in redirect
and title.
* category will only be highlighted if there isn't a match in section_heading,
redirect, or title.

The ```remove_high_freq_terms_from_common_terms``` option can be used to
highlight common terms when using the ```common_terms``` query. It defaults to
```true``` meaning common terms will not be highlighted. Setting it to
```false``` will highlight common terms in ```common_terms``` queries. Note
that this behavior was added in 1.3.1, 1.4.3, and 1.5.0 and before that common
terms were always highlighted by the ```common_terms``` query.

The ```max_expanded_terms``` option can be used to control how many terms the
highlighter expands multi term queries into. The default is 1024 which is the
same as the ```fvh```'s default. Note that the highlighter doesn't need to
expand all multi term queries because it has special handling for many of them.
But when it does, this is how many terms it expands them into. This was added
in 1.3.1, 1.4.3, and 1.5.0 and before the value was hard coded to 100.

The ```return_offsets``` option changes the results from a highlighted string
to the offsets in the highlighted that would have been highlighted. This is
useful if you need to do client side sanity checking on the highlighting.
Instead of a marked up snippet you'll get a result like ```0:0-5,18-22:22```.
The outer numbers are the start and end offset of the snippet. The pairs of
numbers separated by the ```,```s are the hits. The number before the ```-```
is the start offset and the number after the ```-``` is the end offset.
Multi-valued fields have a single character worth of offset between them.


Offsets in postings or term vectors
-----------------------------------
Since adding offsets to the postings (set ```index_options``` to ```offsets```
in OpenSearch) and creating term vectors with offsets (set ```term_vector```
to ```with_positions_offsets``` in OpenSearch) both act to speed up
highlighting of this highlighter you have a choice which one to use.  Unless
you have a compelling reason to use term vectors, go with adding offsets to the
postings because that is faster (by my tests, at least) and uses much less
space.
