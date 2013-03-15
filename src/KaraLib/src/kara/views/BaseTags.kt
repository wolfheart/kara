package kara.views

import java.util.ArrayList
import java.util.HashMap
import kara.styles.Stylesheet
import java.util.Stack
import java.io.PrintWriter
import kara.config.AppConfig
import kara.views.Attribute
import kara.views.Attributes
import kara.controllers.Request
import kara.controllers.Link
import kara.controllers.link


/**
 * Keeps track of the stack of tags that are currently being rendered.
 */
class TagStack(val initial : Tag) {

    val stack = Stack<Tag>();

    {
        stack.push(initial)
    }

    public val top : Tag
        get() = stack.last()

    public fun push(tag : Tag) {
        stack.push(tag)
    }

    public fun pop(tag : Tag) {
        if (tag == top)
            stack.pop()
    }
}


trait Element {
    fun render(appConfig : AppConfig, builder : StringBuilder, indent : String)

    fun toString(appConfig : AppConfig) : String? {
        val builder = StringBuilder()
        render(appConfig, builder, "")
        return builder.toString()
    }
}

class TextElement(val text : String) : Element {
    override fun render(appConfig : AppConfig, builder : StringBuilder, indent : String) {
        builder.append("$indent$text\n")
    }
}

abstract class Tag(val tagName : String, val isEmpty : Boolean) : Element {
    val children : MutableList<Element> = ArrayList<Element>()
    private val attributes = HashMap<String, String>()

    public var tagStack : TagStack? = null

    protected fun initTag<T : Tag>(tag : T, init : T.() -> Unit) : T {
        tag.tagStack = this.tagStack
        if (tagStack != null)
            tagStack?.push(tag)
        tag.init()
        children.add(tag)
        if (tagStack != null)
            tagStack?.pop(tag)
        return tag
    }

    override fun render(appConfig : AppConfig, builder : StringBuilder, indent : String) {
        if (isEmpty) {
            builder.append("$indent<$tagName${renderAttributes()}/>\n")
        }
        else {
            if (children.count() == 1 && children[0] is TextElement) { // for single text elements, render inline
                builder.append("$indent<$tagName${renderAttributes()}>")
                builder.append((children[0] as TextElement).text)
                builder.append("</$tagName>\n")
            }
            else { // more than one text element or a tag child
                builder.append("$indent<$tagName${renderAttributes()}>\n")
                for (c in children) {
                    c.render(appConfig, builder, indent + "  ")
                }
                builder.append("$indent</$tagName>\n")
            }
        }
    }

    protected fun renderAttributes() : String? {
        val builder = StringBuilder()
        for (a in attributes.keySet()) {
            val attr = attributes[a]!!
            if (attr.length > 0) {
                builder.append(" $a=\"${attr}\"")
            }
        }
        return builder.toString()
    }

    public fun attribute(name: String, value: String) {
        attributes[name] = value
    }

    public fun get<T>(attr : Attribute<T>) : T {
        val answer = attributes[attr.name]
        if (answer == null) throw RuntimeException("Atrribute ${attr.name} is missing")
        return attr.decode(answer)
    }

    public fun set<T>(attr: Attribute<T>, value : T) {
        attributes[attr.name] = attr.encode(value)
    }

    public fun get(attributeName : String) : String {
        val answer = attributes[attributeName]
        if (answer == null) throw RuntimeException("Atrribute $attributeName is missing")
        return answer
    }

    public fun set(attName: String, attValue: String) {
        attributes[attName] = attValue
    }
}

abstract class TagWithText(name : String, isEmpty : Boolean) : Tag(name, isEmpty) {
    /**
     * Override the plus operator to add a text element.
     */
    fun String.plus() {
        children.add(TextElement(this))
    }

    /**
     * Yet another way to set the text content of the node.
     */
    var text : String?
        get() {
            if (children.size > 0)
                return children[0].toString()
            return ""
        }
        set(value) {
            children.clear()
            if (value != null)
                children.add(TextElement(value))
        }
}

open class HTML() : TagWithText("html", false) {
    fun head(init : HEAD.() -> Unit) = initTag(HEAD(), init)

    fun body(init : Body.() -> Unit) = initTag(Body(), init)

    public var doctype : String = "<!DOCTYPE html>"

    override fun render(appConfig : AppConfig, builder: StringBuilder, indent: String) {
        builder.append("$doctype\n")
        super<TagWithText>.render(appConfig : AppConfig, builder, indent)
    }
}

class HEAD() : TagWithText("head", false) {
    fun title(init : TITLE.() -> Unit = {}) = initTag(TITLE(), init)

    fun title(text : String) {
        var tag = initTag(TITLE(), {})
        tag.text = text
    }

    fun link(href : Link, rel : String = "stylesheet", mimeType : String = "text/javascript") {
        val tag = initTag(LINK(), {})
        tag.href = href
        tag.rel = rel
        tag.mimeType = mimeType
    }

    fun meta(name : String, content : String) {
        val tag = initTag(META(), {})
        tag.name = name
        tag.content = content
    }

    fun script(src : Link, mimeType : String = "text/javascript") {
        val tag = initTag(SCRIPT(), {})
        tag.src = src
        tag.mimeType = mimeType
    }

    fun style(media : String = "all", mimeType : String = "text/css", init : Stylesheet.() -> Unit) {
        val stylesheet = object : Stylesheet() {
            override fun render() {
                this.init()
            }
        }
        val tag = initTag(STYLE(stylesheet), {})
        tag.media = media
        tag.mimeType = mimeType
    }

    fun stylesheet(stylesheet : Stylesheet) {
        initTag(STYLESHEETLINK(stylesheet), {})
    }
}

class LINK() : TagWithText("link", true) {
    public var href : Link
        get() = this[Attributes.href]
        set(value) {
            this[Attributes.href] = value
        }
    public var rel : String
        get() = this[Attributes.rel]
        set(value) {
            this[Attributes.rel] = value
        }
    public var mimeType : String
        get() = this[Attributes.mimeType]
        set(value) {
            this[Attributes.mimeType] = value
        }
    {
        rel = "stylesheet"
        mimeType = "text/css"
    }
}

class META() : TagWithText("meta", true) {
    public var name : String
        get() = this[Attributes.name]
        set(value) {
            this[Attributes.name] = value
        }
    public var content : String
        get() = this["content"]
        set(value) {
            this["content"] = value
        }
}

class SCRIPT() : TagWithText("script", false) {
    public var src : Link
        get() = this[Attributes.src]
        set(value) {
            this[Attributes.src] = value
        }
    public var mimeType : String
        get() = this[Attributes.mimeType]
        set(value) {
            this[Attributes.mimeType] = value
        }
    {
        mimeType = "text/javascript"
    }
}

/** Stores a stylesheet inline */
class STYLE(val stylesheet : Stylesheet) : TagWithText("style", false) {
    public var media : String
        get() = this["media"]
        set(value) {
            this["media"] = value
        }
    public var mimeType : String
        get() = this[Attributes.mimeType]
        set(value) {
            this[Attributes.mimeType] = value
        }
    {
        media = "all"
        mimeType = "text/css"
    }

    override fun render(appConfig : AppConfig, builder: StringBuilder, indent: String) {
        builder.append("$indent<$tagName${renderAttributes()}>\n")
        builder.append(stylesheet.toString())
        builder.append("$indent</$tagName>\n")
    }
}

class STYLESHEETLINK(var stylesheet : Stylesheet) : TagWithText("link", true) {
    public var href : Link
        get() = this[Attributes.href]
        set(value) {
            this[Attributes.href] = value
        }
    public var rel : String
        get() = this[Attributes.rel]
        set(value) {
            this[Attributes.rel] = value
        }
    public var mimeType : String
        get() = this[Attributes.mimeType]
        set(value) {
            this[Attributes.mimeType] = value
        }
    {
        rel = "stylesheet"
        mimeType = "text/css"
    }


    override fun render(appConfig : AppConfig, builder: StringBuilder, indent: String) {
        stylesheet.write(appConfig)
        href = stylesheet.relativePath(appConfig).link()
        super<TagWithText>.render(appConfig, builder, indent)
    }
}

class TITLE() : TagWithText("title", false)