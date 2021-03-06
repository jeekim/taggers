package org.allenai.taggers.tag

import org.allenai.common.immutable.Interval
import org.allenai.nlpstack.core.Tokenizer
import org.allenai.nlpstack.core.repr.{Chunks, Lemmas}
import org.allenai.nlpstack.core.typer.Type
import org.allenai.taggers.NamedGroupType
import org.allenai.taggers.pattern.{PatternBuilder, TypedToken}

import edu.knowitall.openregex
import edu.washington.cs.knowitall.regex.Expression.NamedGroup

import scala.collection.immutable.Range
import scala.util.control._

/** Run a token-based pattern over the text and tag matches.
  *
  * @author schmmd
  *
  */
class OpenRegex(patternTaggerName: String, expression: String) extends Tagger[Tagger.Sentence with Chunks with Lemmas] {
  override def name = patternTaggerName
  override def source = null

  val pattern: openregex.Pattern[PatternBuilder.Token] =
    try {
      PatternBuilder.compile(expression)
    }
    catch {
      case NonFatal(e) =>
        throw new OpenRegex.OpenRegexException(s"Could not compile pattern for $patternTaggerName.", e)
    }

  /** The constructor used by reflection.
    *
    * Multiple lines are collapsed to create a single expression.
    */
  def this(name: String, expressionLines: Seq[String]) {
    this(name, expressionLines.mkString(" "))
  }

  /** This method overrides Tagger's default implementation. This
    * implementation uses information from the Types that have been assigned to
    * the sentence so far.
    */
  override def tag(sentence: TheSentence,
    originalTags: Seq[Type]): Seq[Type] = {

    // convert tokens to TypedTokens
    val typedTokens = OpenRegex.buildTypedTokens(sentence, originalTags)

    val tags = for {
      tag <- this.findTags(typedTokens, sentence, pattern)
    } yield (tag)

    return tags
  }

  /** This is a helper method that creates the Type objects from a given
    * pattern and a List of TypedTokens.
    *
    * Matching groups will create a type with the name or index
    * appended to the name.
    *
    * @param typedTokenSentence
    * @param sentence
    * @param pattern
    * @return
    */
  protected def findTags(typedTokenSentence: Seq[TypedToken],
    sentence: TheSentence,
    pattern: openregex.Pattern[TypedToken]) = {

    var tags = Seq.empty[Type]

    val matches = pattern.findAll(typedTokenSentence);
    for (m <- matches) {
      val groupSize = m.groups.size
      var parent: Option[Type] = None
      for (i <- 0 until groupSize) {
        val group = m.groups(i);

        val tokens = sentence.lemmatizedTokens.slice(group.interval.start, group.interval.end).map(_.token)
        val text = tokens match {
          case head +: _ => Tokenizer.originalText(tokens, head.offsets.start)
          case Seq() => ""
        }
        val tag = group.expr match {
          // create the main type for the group
          case _ if i == 0 =>
            val typ = Type(this.name, this.source, OpenRegex.bridgeInterval(group.interval), text)
            parent = Some(typ) // There may be children of this type.
            Some(typ)
          case namedGroup: NamedGroup[_] =>
            require(parent.isDefined)
            val name = this.name + "." + namedGroup.name
            Some(new NamedGroupType(namedGroup.name, Type(name, this.source, OpenRegex.bridgeInterval(group.interval), text), parent))
          case _ => None
        }

        tag.foreach { t =>
          tags = tags :+ t
        }
      }
    }

    tags
  }
}

object OpenRegex {
  class OpenRegexException(message: String, cause: Throwable)
  extends Exception(message, cause)

  def bridgeInterval(range: Range): org.allenai.common.immutable.Interval = {
    Interval.open(range.start, range.end)
  }

  def buildTypedTokens(sentence: Tagger.Sentence with Chunks with Lemmas, types: Seq[Type]) = {
    for ((token, i) <- sentence.lemmatizedTokens.zipWithIndex) yield {
      val availableTypes = sentence.availableTypes(i, types)
      val consumed = sentence.consumingTypes(i).isDefined
      new TypedToken(token, i, availableTypes.toSet, consumed)
    }
  }
}
