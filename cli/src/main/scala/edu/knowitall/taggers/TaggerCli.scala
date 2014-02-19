package edu.knowitall.taggers

import edu.knowitall.repr.sentence
import edu.knowitall.repr.sentence.Chunker
import edu.knowitall.repr.sentence.Chunks
import edu.knowitall.repr.sentence.Lemmas
import edu.knowitall.repr.sentence.Lemmatizer
import edu.knowitall.repr.sentence.Sentence
import edu.knowitall.taggers.rule._
import edu.knowitall.taggers.tag.Tagger
import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.tool.typer.Type

import edu.knowitall.common.Resource.using

import java.io.File
import scala.io.Source

class TaggerApp(cascade: Cascade[Tagger.Sentence with Chunks with Lemmas]) {
  type Sent = Tagger.Sentence with Chunks with Lemmas
  val chunker = new OpenNlpChunker()

  def format(typ: Type) = {
    typ.name + "(" + typ.text + ")"
  }

  def process(text: String): Sent = {
    new Sentence(text) with Consume with Chunker with Lemmatizer {
      val chunker = TaggerApp.this.chunker
      val lemmatizer = MorphaStemmer
    }
  }

  def apply(sentence: Sent): (Seq[String], Seq[String]) = {
    val (types, extractions) = cascade apply sentence
    (types.reverse map format, extractions)
  }
}

object TaggerCliMain {
  case class Config(cascadeFile: File = null,
      sentencesFile: Option[File] = None,
      private val _outputTypes: Boolean = false,
      private val _outputExtractions: Boolean = false) {

    // If no outputs are specified, output them all.
    def outputTypes = (!_outputTypes && !_outputExtractions) || _outputTypes
    def outputExtractions = (!_outputTypes && !_outputExtractions) || _outputExtractions

    def sentenceSource() = sentencesFile match {
      case Some(file) => Source.fromFile(file)
      case None => Source.fromInputStream(System.in)
    }

    def cascadeSource() = Source.fromFile(cascadeFile)
  }

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Config]("taggers") {
      arg[File]("<file>") action { (x, c) =>
        c.copy(cascadeFile = x)
      } text ("file specifying cascade")

      opt[File]('s', "sentences-file") action { (x, c) =>
        c.copy(sentencesFile = Some(x))
      } text ("file containing sentences")

      opt[Unit]("outputTypes") action { (x, c) =>
        c.copy(_outputTypes = true)
      } text ("output the types found")

      opt[Unit]("outputExtractions") action { (x, c) =>
        c.copy(_outputExtractions = true)
      } text ("output the types found")
    }

    parser.parse(args, Config()) match {
      case Some(config) => run(config)
      case None =>
    }
  }

  def run(config: Config): Unit = {
    val cascade = Cascade.load(config.cascadeFile)

    val app = new TaggerApp(cascade)

    using(config.sentenceSource()) { source =>
      // iterate over sentences
      for (line <- source.getLines) {
        val sentence = app.process(line)
        val (types, extractions) = app(sentence)
        if (config.outputExtractions) extractions foreach println
        if (config.outputTypes) types foreach println
        println()
      }
    }
  }
}
