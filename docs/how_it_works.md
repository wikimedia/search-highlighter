How does it work?
=================

The highlighter has a few key types of components: <dl>
<dt>HitEnum</dt><dd>Enumerates hits (aka matches)</dd>
<dt>SnippetChooser</dt><dd>Uses HitEnum to pick snippets</dd>
<dt>Segmenter</dt><dd>Plugged into AbstractBasicSnippetChooser implementations
to pick where snippets (aka fragments) begin and end.</dt>
</dl>


HitEnum
-------
Comes in two flavors:<dl>
<dt>Plain HitEnums</dt>
<dd>Actually pulls hits from the source document.  Examples are
    DocsAndPositionsHitEnum, TokenStreamHitEnum, and BreakIteratorHitEnum.</dd>
<dt>Transforming HitEnums</dt>
<dd>Wraps and transforms one or more HitEnums.  Some transforms are simple like
    WeightFilterHitEnumWrapper or PositionBoostingHitEnumWrapper.  Some are
    much more involved like PhraseHitEnumWrapper and
    MergingHitEnum.  HitEnums that wrap a single HitEnum should be named
    FooHitEnumWrapper.  I don't have a consisten naming scheme for those that
    wrap more then one HitEnum.</dd>
</dl>


SnippetChooser
--------------
There are two implementations:<dl>
<dt>BasicScoreBasedSnippetChooser</dt>
<dd>Score ordered and score cutoff snippets.  Keeps snippets in a priority
    queue then sorts them in document order to pick bounds so there is no
    overlap then optionally sorts them in score order. Worst case performance
    is ```O(n*log(m) + m*log(m))``` where n is number of snippets found and m
    is number of snippets requested.  The first term is scanning all the
    segments and the second is the sorts.  You can put an upper bound on n by
    setting ```maxSnippetsChecked``` which is piped through to Elasticsearch as
    ```max_fragments_scored```.</dd>
<dt>BasicSourceOrderSnippetChooser</dt>
<dd>Source order fragments.  Can be much faster because it can exit after
    hitting the first snippet.</dd>
</dl>


Segmenter
---------
Four major implementations:<dl>
<dt>CharScanningSegmenter</dt>
<dd>FastVectorHighlighter like character scanning.  Usually the fastest choice
    for large text.</dd>
<dt>BreakIteratorSegmenter</dt>
<dd>PostingHighlighter like sentence breaks.  Slower but sometimes prettier.
    Suffers if text isn't 100% prose and/or some sentences are hugely
    long.</dd>
<dt>WholeSourceSegmenter</dt>
<dd>Doesn't break the source at all.</dd>
<dt>MultiSegmenter</dt>
<dd>Wraps many segmenters adding hard stops between them.  The life blood of
    multi valued fields.</dd>
</dl>

The Elasticsearch plugin adds a DelayedSegmenter which constructs one of the
first three segmenters lazily to prevent loading the field until the first hit
is found.


Others
---------------
SourceExtracters are responsible for extracting the snippets from the source
once they are identified.
