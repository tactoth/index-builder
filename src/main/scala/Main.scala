import java.io.File
import java.util
import java.util.regex.Pattern

import com.beust.jcommander.{JCommander, Parameter}

import scala.collection.mutable

/**
 * Index generator
 *
 * index-builder -pattern (\w+)/1x_web/ic_(\w+)_black_18dp\\.png -result $1/$2 dir
 *
 *
 * Created by liuwei on 2/27/16.
 */
object Main {

  object params {
    @Parameter(names = Array("-pattern"), description = "The pattern to find the files to include in the index", required = true)
    var pathPattern: String = null

    @Parameter(names = Array("-result"), description = "The result format, i.e. $1/$2/$3", required = true)
    var resultFormat: String = null

    @Parameter(description = "The folder to generate the index file", required = true)
    var dirs: util.List[String] = new util.ArrayList[String]()
  }

  /*
  For building the tree
   */
  trait Node {
    val name: String
  }

  case class Category(name: String, children: mutable.Buffer[Node] = mutable.Buffer()) extends Node {
    def getChildOrCreate[NodeType <: Node](name: String)(creator: => NodeType): NodeType = {
      for (node <- children) {
        if (node.name == name) {
          return node.asInstanceOf[NodeType]
        }
      }
      val node = creator
      children += node
      node
    }

    def addNodeUnderPath(segments: List[String], node: Node) {
      segments match {
        case Nil => // pass
        case last :: Nil => getChildOrCreate(last)(node)
        case next :: remaining =>
          getChildOrCreate(next)(new Category(next)).addNodeUnderPath(remaining, node)
      }
    }
  }

  case class Leaf(name: String, path: String) extends Node {}

  def visitWithRelativePath(root: File)(withRelativePath: String => Unit): Unit = {
    val rootPath = root.getAbsolutePath
    val rootLength = rootPath.length + (if (rootPath.endsWith("/")) 0 else 1)

    val q = mutable.Queue[File](root)
    while (q.nonEmpty) {
      val file = q.dequeue()
      if (file.isDirectory) {
        val children = file.listFiles()
        if (children != null) {
          q.enqueue(children: _*)
        }
      } else {
        val path = file.getAbsolutePath
        val relativePath = path.substring(rootLength)
        withRelativePath(relativePath)
      }
    }
  }

  val PATTERN_PARAM_REF = Pattern.compile("\\$(\\d)")

  def applyResult(input: Seq[String], resultFormat: String) = {
    val matcher = PATTERN_PARAM_REF.matcher(resultFormat)
    val result = mutable.StringBuilder.newBuilder

    var start = 0
    while (matcher.find(start)) {
      val keep = resultFormat.substring(start, matcher.start())
      result ++= keep

      // append the referenced param
      val referenceIndex = matcher.group(1).toInt
      result ++= input(referenceIndex)

      start = matcher.end()
    }

    result ++= resultFormat.substring(start)

    // result
    result.toString()
  }

  def main(args: Array[String]) {
    val commander = new JCommander()
    commander.addObject(params)

    if (args.isEmpty) {
      commander.usage()
      return
    }

    try {
      commander.parse(args: _*)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        commander.usage()
        return
    }

    // the root node
    val rootNode = new Category("index")

    val pathPattern = Pattern.compile(params.pathPattern)
    visitWithRelativePath(new File(params.dirs.get(0))) {
      relPath =>
        val matcher = pathPattern.matcher(relPath)
        if (matcher.find()) {
          // this path is what we are interested
          val matchParams = Range.inclusive(0, matcher.groupCount()).map(matcher.group).toSeq
          val resultPath = applyResult(matchParams, params.resultFormat)
          val segments = resultPath.split("/")

          Console.err.println(s"$resultPath: $relPath")

          rootNode.addNodeUnderPath(segments.toList, new Leaf(segments.last, relPath))
        }
    }

    /**
     * Please notice: parents are reveresd, parents.head is the immediate parent
     */
    def traverse(parents: List[String],
                 node: Node,
                 onCategory: (List[String], Category) => String,
                 combineCategory: (String, Traversable[String]) => String,
                 onLeaf: (List[String], Leaf) => String): String = {
      node match {
        case category: Category =>
          combineCategory(
            onCategory(parents, category),
            for (child <- category.children) yield traverse(category.name :: parents, child, onCategory, combineCategory, onLeaf)
          )
        case leaf: Leaf =>
          onLeaf(parents, leaf)
      }
    }

    def buildHtmlId(parents: List[String], name: String) = (name :: parents).reverse.mkString("_")

    def buildCategoryTitle(category: Category) = s"${category.name} (${category.children.size})"

    def buildOutline(category: Category): String = {
      "<ul>" + traverse(
        Nil,
        category,
        onCategory = { (parents, category) =>
          s"""<li><a href="#${buildHtmlId(parents, category.name)}">${buildCategoryTitle(category)}</a></li>"""
        },
        combineCategory = { (categoryRepr, childrenRepr) =>
          val childrenAsText = childrenRepr.mkString("\n").trim
          if (childrenAsText.isEmpty)
            categoryRepr
          else
            s"$categoryRepr\n<ul>\n$childrenAsText\n</ul>"
        },
        onLeaf = { (parents, leaf) =>
          // ignore leafs
          ""
        }) + "</ul>"
    }

    // build the html
    def buildHtml(node: Node): String = {
      traverse(
        Nil,
        node,
        onCategory = { (parents, category) =>
          val level = parents.size + 1
          s"""<h$level id="${buildHtmlId(parents, category.name)}">${buildCategoryTitle(category)}</h$level>"""
        },
        combineCategory = { (categoryRepr, childrenRepr) =>
          categoryRepr + childrenRepr.mkString("\n")
        },
        onLeaf = { (parents, leaf) =>
          val Leaf(name, path) = leaf
          if (Seq("png", "jpg", "gif").exists(path.endsWith)) {
            s"""<a href="$path">$name <img src="$path"/></a>"""
          } else {
            s"""<a href="$path">$name</a>"""
          }
        })
    }

    println(buildOutline(rootNode) + "\n" + buildHtml(rootNode))
  }
}
