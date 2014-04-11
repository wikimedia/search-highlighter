Fetch Fields
============
The ```fetched_fields``` option can be used to return fields next to the
highlighted field.  It is designed for use with object fields but has a number
of limitations.  For example, while highlighting the ```foo``` field in the
document below it can be used to fetch the "bar" field:
```js
{
  "test": [
    { "foo": "skipped", "bar": 1 },
    { "foo": "matched", "bar": 2 }
  ]
}
```
Here is the query:
```js
{
  "_source": false,
  "query": {
    "term": {
      "test.foo": "matched"
    }
  },
  "highlight": {
    "fields": {
      "test.foo": {
        "type": "experimental",
        "options": {
          "fetch_fields": [ "test.bar" ]
        }
      }
    }
  }
}
```
And here is the interesting part of the output:
```js
  "highlight" : {
    "foo" : [ "<em>matched</em>", "2" ]
  }
```
Notice that ```2``` is returned as a string and just looks like another
segment.  This return format is a compromize with the way Elasticsearch expects
segments to be returned.  It doesn't count against the segment limit.  It just
looks a bit funny.

But this document won't work:
```js
{
  "test": [
    { "foo": "skipped" },
    { "foo": "matched", "bar": 2 }
  ]
}
```

The thing is, ```fetched_fields``` uses a trick to get pick the right field to
fetch: in most documents the ordinal position each all the multifields line up.
Meaning, this document works just fine:
```js
{
  "test": [
    { "foo": "skipped", "bar": "" },
    { "foo": "matched", "bar": 2 }
  ]
}
```
and so does this one:
```js
{
  "test": [
    { "foo": ["skipped", "matched"], "bar": [1, 2] }
  ]
}
```

Use it, but be careful.