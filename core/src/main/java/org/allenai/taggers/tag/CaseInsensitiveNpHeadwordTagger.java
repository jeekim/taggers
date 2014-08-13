package org.allenai.taggers.tag;

import org.allenai.nlpstack.core.ChunkedToken;
import org.allenai.nlpstack.core.Lemmatized;
import org.allenai.nlpstack.core.typer.Type;

import java.util.List;

/***
 * Search for exact keyword matches, ignoring case, and then tag the chunk
 * if the keyword is the headword of that chunk.
 * @author schmmd
 *
 */
public class CaseInsensitiveNpHeadwordTagger extends CaseInsensitiveKeywordTagger {
    public CaseInsensitiveNpHeadwordTagger(String name, List<String> keywords) {
        super(name, keywords);
    }

    /**
     * Constructor used by reflection.
     * @param name name of the tagger
     * @param args arguments to the tagger
     */
    public CaseInsensitiveNpHeadwordTagger(String name, scala.collection.Seq<String> args) {
        this(name, scala.collection.JavaConversions.asJavaList(args));
    }

    @Override
    public List<Type> findTagsJava(final List<Lemmatized<ChunkedToken>>  sentence) {
        List<Type> keywordTags = super.findTagsJava(sentence);
        return AfterTaggers.tagHeadword(keywordTags, sentence);
    }
}
