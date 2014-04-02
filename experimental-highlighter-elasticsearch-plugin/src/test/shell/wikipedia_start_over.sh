#!/bin/bash

curl -XDELETE "http://localhost:9200/wikipedia?pretty"
curl -XPOST "http://localhost:9200/wikipedia?pretty" -d'{
  "settings": {
    "index" : {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  }
}'
curl -XPUT http://localhost:9200/wikipedia/page/_mapping?pretty -d '{
  "properties": {
    "category": {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    },
    "disambiguation": {
      "type": "boolean"
    },
    "link": {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    },
    "redirect": {
      "type": "boolean"
    },
    "redirect_page": {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    },
    "special": {
      "type": "boolean"
    },
    "stub": {
      "type": "boolean"
    },
    "text": {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    },
    "title": {
      "type": "string",
      "index_options": "offsets",
      "term_vector": "with_positions_offsets"
    }
  }
}'
curl -XPUT localhost:9200/_river/wikipedia/_meta?pretty -d '{
  "type" : "wikipedia"
}'
