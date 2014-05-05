#!/bin/bash

curl -XDELETE "http://localhost:9200/test?pretty"
curl -XPOST "http://localhost:9200/test?pretty"
curl -XPUT http://localhost:9200/test/test/_mapping?pretty -d'{
  "properties": {
    "title" : {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    }
  }
}'

curl -XPOST "http://localhost:9200/test/test?pretty" -d '{"title": "a pretty tiny string to test with "}'
echo '{"title": "a much larger string to test with ' > /tmp/largerString
echo '{"title": "huge string with ' > /tmp/hugeString
echo '{"title": "many string with ' > /tmp/manyString
echo '{"title": ["multi"' > /tmp/multiString
rm -f /tmp/hugePart
for i in {1..100}; do
  echo "very very very " >> /tmp/hugePart
done
rm -f /tmp/manyPart
for i in {1..100}; do
  echo "very very many " >> /tmp/manyPart
done
for i in {1..1000}; do
  echo 'much much more text.  ' >> /tmp/largerString
  cat /tmp/hugePart >> /tmp/hugeString
  echo 'much much more text.  ' >> /tmp/hugeString
done
for i in {1..100}; do
  cat /tmp/manyPart >> /tmp/manyString
  echo 'much much more text.  ' >> /tmp/manyString
done
for i in {1..10000}; do
  echo ', "multi"' >> /tmp/multiString
done
echo 'and larger at the end"}' >> /tmp/largerString
echo 'and huge at the end"}' >> /tmp/hugeString
echo '"}' >> /tmp/manyString
echo ']}' >> /tmp/multiString
curl -XPOST "http://localhost:9200/test/test?pretty" -d @/tmp/largerString
curl -XPOST "http://localhost:9200/test/test?pretty" -d @/tmp/hugeString
curl -XPOST "http://localhost:9200/test/test?pretty" -d @/tmp/manyString
curl -XPOST "http://localhost:9200/test/test?pretty" -d @/tmp/multiString
curl -XPOST http://localhost:9200/test/_refresh?pretty

function go() {
  highlighter="$1"
  count=200
  hit_source=""
  fragmenter=""
  if [ "$highlighter" == "experimental" ]; then
    hit_source="$2"
    fragmenter="$3"
  fi
  if [ "$highlighter" = "plain" ]; then
    fragmenter="span"
    count=50
  fi
  echo '{
  "_source": false,
  "query": {
    "query_string": {
      "query": "'$search'"
    }
  },
  "highlight": {
    "order": "'$order'",
    "options": {
      "hit_source": "'$hit_source'"
    },
    "fields": {
      "title": {
        "number_of_fragments": '$number_of_fragments',
        "type": "'$highlighter'",
        "fragmenter": "'$fragmenter'"
      }
    }
  }
}' > /tmp/post
  printf "%15s %20s %7s %10s %10s %1s " "$highlighter" "$search" "$order" "$hit_source" "$fragmenter" "$number_of_fragments"
  ab -c 3 -n $count -p /tmp/post http://localhost:9200/test/_search 2>&1 | grep Total:
}

function each() {
  go plain
  go fvh
  go postings
  go experimental postings scan
  go experimental postings sentence
  go experimental postings none
  go experimental vectors scan
  go experimental analyze scan
}

function suite() {
  for order in score source; do
    export order=$order

    export search=tiny
    export number_of_fragments=1
    each
    export search=larger
    each
    export number_of_fragments=2
    each
    export search=huge
    each
    export search=many
    each
    export search="hug* AND str*"
    each
    export search=multi
    each
    export search="\\\"huge string\\\""
    each
    export search="huge string"
    each
  done
}

export mode=bench
suite
