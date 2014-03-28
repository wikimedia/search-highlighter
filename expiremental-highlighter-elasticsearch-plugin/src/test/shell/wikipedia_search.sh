function search() {
  search="$1"
  echo '
{
  "_source": false,
  "size": 50,
  "query": {
    "query_string": {
      "query": "'$search'",
      "fields": ["title^20", "text", "category^3", "link^3"]
    }
  },
  "highlight": {
    "type": "expiremental",
    "fields": {
      "text": {
        "fragmenter": "sentence",
        "order": "score",
        "options": {
          "hit_source": "postings"
        }
      },
      "title": {
        "fragmenter": "none",
        "order": "source",
        "options": {
          "hit_source": "analyze"
        }
      },
      "category": {
        "fragmenter": "none",
        "order": "source",
        "options": {
          "hit_source": "postings"
        }
      },
      "link": {
        "fragmenter": "none",
        "order": "source",
        "options": {
          "hit_source": "postings"
        }
      }
    }
  }
}' > /tmp/post
  # curl -XPOST 'http://localhost:9200/wikipedia/_search?pretty' -d @/tmp/post
  ab -c 3 -n 50 -p /tmp/post 'http://localhost:9200/wikipedia/_search' 2>&1 | grep Total:
}

search 'Main Page'
