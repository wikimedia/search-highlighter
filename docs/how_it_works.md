How does it work?
=================

This highlighter was created to unify some of the good ideas in the three
highlighters already in Lucene:
* Finding hits should be possible by:
 * Reanalyzing the field's contents
 * Reading term vectors
 * Reading offsets from the postings
* Snippets should be split on:
 * Sentence boundaries
 * Word boundaries
 * Some arbitrary list of characters that is quick to scan

To do that this project builds a set of composable components and an
Elasticsearch plugin that strings them all together.

From front to back:
* the query is decomposed into a tree of ```HitEnums```
* which are iterated by a ```SnippetChooser```
* which determines snippet boundaries using a ```Segmenter```
* and scores the ```Snippets``` using a ```SnippetWeigher```
* ultimately producing a ```List<Snippet>```
* which is then turned into ```String```s by a ```SnippetFormatter```.

HitEnum
-------
Comes in two flavors:
* Source ```HitEnums``` pulls hits from the source document. Examples are
```PostingsHitEnum```, ```TokenStreamHitEnum```, and
```BreakIteratorHitEnum```.
* Transforming ```HitEnums``` wrap and transform one or more ```HitEnums```.
Some transforms are simple like ```WeightFilterHitEnumWrapper``` or
```PositionBoostingHitEnumWrapper```.  Some are much more involved like
```PhraseHitEnumWrapper``` and ```MergingHitEnum```. ```HitEnums``` that wrap a
single ```HitEnum``` should be named ```FooHitEnumWrapper```.  There isn't a
consistent naming scheme for those that wrap more than one ```HitEnum```.

Enum in this context is a riff on Lucene's ```TermsEnum```. Its just a
convenient way of iterating without returning whole objects at each iteration.
It looks like:

```java
public interface HitEnum {
  /**
   * Move the enum to the next hit.
   *
   * @return is there a next hit (true) or was the last one the final hit
   *         (false)
   */
  boolean next();

  /**
   * Ordinal position relative to the other terms in the text. Starts at 0.
   */
  int position();

  /**
   * The start offset of the current term within the text.
   */
  int startOffset();

  ...
}
```
Its not as fancy as ```TermsEnum``` with its nifty ```AttributeSource``` but it
gets the job done.

The idea is that you make one or more source ```HitEnums``` and wrap them in
transforming ```HitEnums```. For testing most things we use
```BreakIteraorHitEnum``` for the source ```HitEnum``` because it's simple. In
the Elasticsearch plugin we use ```TokenStreamHitEnum``` when we have to
reanalyze a string and ```PostingsHitEnum``` when we can read the hits
from term vectors or the postings list. It also uses ```RegexHitEnum``` and
```AutomatonHitEnum``` for regular expression highlighting.

The idea is to build a tree of ```HitEnum```s so that you can ultimately pass
a single one to the ```SnipppetChooser``` and they all work together to iterate
the hits. For example, you can limit the highlighting to terms that appear in a
phrase by building a chain of ```HitEnum```s like this:

```java
// Note real constructors are more complex. Sorry.
String str = "I like cats but I don't like fish";
HitEnum e = BreakIteratorHitEnum.englishWords(str);
e = new PhraseHitEnumWrapper(e, 10, "like", "cats");
e = new WeightFilteredHitEnumWrapper(e, 1);
while (e.next()) {
    System.out.println(str.substring(e.startOffset(), e.endOffset()));
}
// Prints:
// like
// cats
```

That chain of enums:
1. Returns every word in a sentence with a score of 1.
1. Sets the score of the term ```like``` followed by ```cats``` to 10.
1. Filters out all hits with score not greater than 1.

But wait, there's more! You can merge multiple HitEnums! This is really
important because when you load hits from Lucene's postings or term vectors
they are only for a single term. So it looks like this:

```java
// Note real constructors are more complex. Sorry.
HitEnum l = new PostingsHitEnum(reader, docId, field, "like");
HitEnum c = new PostingsHitEnum(reader, docId, field, "cats");
HitEnum m = new MergingHitEnum(ImmutableList.of(likeEnum, catsEnum),
    HitEnum.LessThans.POSITION);
HitEnum p = new PhraseHitEnumWrapper(e, 10, "like", "cats");
HitEnum f = new WeightFilteredHitEnumWrapper(e, 1);
while (f.next()) {
    System.out.println(str.substring(e.startOffset(), e.endOffset()));
}
// Prints:
// like
// cats
```

This creates a little tree of ```HitEnum```s like this:

```
l     c
 \   /
  \ /
   m
   |
   |
   p
   |
   |
   f
```

Pulling on ```f``` pulls on ```p``` which pulls on ```m``` which pulls on
```l``` or ```c```. It gets much more complicated in the real world with
potentially multiple levels of merging and filtering.

SnippetChooser
--------------
```SnippetChooser```s are responsible for pulling on the HitEnum and picking
the right List<Snippet> to return.
There are two concrete implementations:<dl>
<dt>BasicScoreBasedSnippetChooser</dt>
<dd>Score ordered and score cutoff snippets. Keeps snippets in a priority
    queue then sorts them in document order to pick bounds so there is no
    overlap then optionally sorts them in score order. Worst case performance
    is ```O(n*log(m) + m*log(m))``` where n is number of snippets found and m
    is number of snippets requested.  The first term is scanning all the
    segments and the second is the sorts. You can put an upper bound on n by
    setting ```maxSnippetsChecked``` which is piped through to Elasticsearch as
    ```max_fragments_scored```. You can put an upper bound on m with the
    ```max``` parameter on ```SnippetChooser.choose``` which is piped through
    Elasticsearch as ```number_of_fragments```.</dd>
<dt>BasicSourceOrderSnippetChooser</dt>
<dd>Source order fragments. Can be much faster because it can exit after
    hitting ```number_of_fragments``` snippets.</dd>
</dl>


Segmenter
---------
The ```SnippetChooser``` uses the Segmenter to decide if two hits are part of
the same segment and, once its picked the best segments, to find the bounds of
the segment. That is a lot of words so have an example:

Say you are highlighting the words ```like cats``` in the paragraph:

```
*Cats* are just super duper dandy. Even when they scratch and bit I just *like*
*cats* so much! Man. I *like* *cats*.
```

Assuming you don't want to return the whole silly paragraph, its the
```Segmenter```'s job to decide where to break the paragraph. There are four
major implementation:

The ```CharScanningSegmenter``` does ```FastVectorHighlighter``` like character
scanning. Usually its the fastest choice for large text. It would segment the
paragraph like this:

```
*Cats* are just super
duper dandy. Even when
they scratch and bit I
just *like* *cats* so
much! Man. I *like* *cats*.
```

The ```BreakIteratorSegmenter``` does ```PostingHighlighter``` like sentence or
paragraph breaks. Its slower but sometimes prettier. It isn't prettier if the
text isn't 100% prose or some sentences are hugely long. It would segment the
paragraph like this:

```
*Cats* are just super duper dandy.
Even when they scratch and bit I just *like* *cats* so much!
Man.
I *like* *cats*.
```

The ```WholeSourceSegmenter``` just returns the whole segment all the time.

The ```MultiSegmenter``` wraps many ```Segmenters``` adding hard stops between
them. Its used to prevent segments from spanning multiple values on the same
field.

The Elasticsearch plugin adds a ```DelayedSegmenter``` which constructs one of
the first three ```Segmenters``` lazily to prevent loading the field until the
first hit is found.


Other
-----
The other components listed in the walk-through offer less interesting options
but they are covered below for completeness sake.

The ```SnippetChooser``` delegates scoring each segment to a
```SnippetWeigher```. There are two ```SnippetWeighers```:
* ```SumSnipperWeigher``` just adds up the weight of all of the hits in the
snippet.
* ```ExponentialSnippetWeigher``` tries to rate snippets that contain different
terms more highly by only increasing the score for repeated terms by

The ```Snippets``` are turned into strings by a ```SnippetFormatter``` with the
help of a ```SourceExtracter```. There are two concrete
```SnippperFormatter```s:
* ```SnippetFormatter.Default```: Adds html tags around the hits like
```<em>```.
* ```OffsetSnippetFormatter```: Returns the offsets into the text of the hit
and the rest of the snippet. Faster because it doesn't have to load the text
but useless in many cases. Even in cases where it can be used you have to be
very careful of character encodings as those offsets are for Java strings.

```SourceExtracter```s turn offsets into substrings. There are several
implementations:
* ```StringSourceExtracter``` wraps a string and calls substring.
* ```NonMergingMultiSourceExtracter``` wraps many ```SourceExtracter```s and
makes them look like one ```SourceExtracter``` with the offsets strung together.
If and extraction tries to straddle multiple wrapped ```SourceExtracter```s
then it throws and exception.
* ```StringMergingMultiSourceExtracter``` is just like
```NonMergingMultiSourceExtracter``` but if extractions straddle multiple
wrapped ```SourceExtracter```s then the extracted strings are merged.
