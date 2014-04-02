function search() {
  highlighter="$1"
  search="$2"
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
    "type": "'$highlighter'",
    "fields": {
      "text": {
        "fragmenter": "sentence",
        "order": "score",
        "options": {
          "hit_source": "postings",
          "boost_before": {
            "20": 5,
            "100": 3,
            "500": 1.5
          }
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
  printf "%15s %30s " $highlighter "$search"
  # curl -XPOST 'http://localhost:9200/wikipedia/_search?pretty' -d @/tmp/post
  ab -c 3 -n 50 -p /tmp/post 'http://localhost:9200/wikipedia/_search' 2>&1 | grep Total:
}

search expiremental 'Main Page'
search postings 'Main Page'
search expiremental 'Main'
search postings 'Main'
search expiremental 'Page'
search postings 'Page'
search expiremental 'Marble Staircase'
search postings 'Marble Staircase'
search expiremental 'Lettuce Leaves'
search postings 'Lettuce Leaves'
