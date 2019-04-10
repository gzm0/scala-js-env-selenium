package org.scalajs.jsenv.selenium

import scala.collection.JavaConverters._

import java.io._
import java.nio.file._
import java.net._

import org.scalajs.io._

private[selenium] sealed abstract class FileMaterializer {
  private val tmpSuffixRE = """[a-zA-Z0-9-_.]*$""".r

  private[this] var tmpFiles: List[Path] = Nil

  def materialize(vf: VirtualBinaryFile): URL = {
    val tmp = newTmp(vf.path)
    val in = vf.inputStream
    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING)
    toURL(tmp)
  }

  final def materialize(name: String, content: String): URL = {
    val tmp = newTmp(name)
    Files.write(tmp, Iterable(content).asJava)
    toURL(tmp)
  }

  final def close(): Unit = {
    tmpFiles.foreach(Files.delete)
    tmpFiles = Nil
  }

  private def newTmp(path: String): Path = {
    val suffix = tmpSuffixRE.findFirstIn(path).orNull
    val p = createTmp(suffix)
    tmpFiles ::= p
    p
  }

  protected def createTmp(suffix: String): Path
  protected def toURL(file: Path): URL
}

object FileMaterializer {
  import SeleniumJSEnv.Config.Materialization
  def apply(m: Materialization): FileMaterializer = m match {
    case Materialization.Temp =>
      new TempDirFileMaterializer

    case Materialization.Server(contentDir, webRoot) =>
      new ServerDirFileMaterializer(contentDir, webRoot)
  }
}

/** materializes virtual files in a temp directory (uses file:// schema). */
private class TempDirFileMaterializer extends FileMaterializer {
  override def materialize(vf: VirtualBinaryFile): URL = vf match {
    case vf: FileVirtualBinaryFile => vf.file.toURI.toURL
    case vf                        => super.materialize(vf)
  }

  protected def createTmp(suffix: String) = Files.createTempFile(null, suffix)
  protected def toURL(file: Path): URL = file.toUri.toURL
}

private class ServerDirFileMaterializer(contentDir: Path, webRoot: URL)
    extends FileMaterializer {
  Files.createDirectories(contentDir)

  protected def createTmp(suffix: String) =
    Files.createTempFile(contentDir, null, suffix)

  protected def toURL(file: Path): URL = {
    val rel = contentDir.relativize(file)
    assert(!rel.isAbsolute)
    val nameURI = new URI(null, null, rel.toString, null)
    webRoot.toURI.resolve(nameURI).toURL
  }
}
