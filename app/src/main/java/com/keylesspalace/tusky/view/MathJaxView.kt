/*
* This class created by quantaDot https://github.com/quantaDot/MathRenderer/blob/main/MathRendererLib/src/main/java/com/qdot/mathrendererlib/MathRenderView.kt
* And updated by bonnjalal  https://github.com/bonnjalal
*/
package com.keylesspalace.tusky.view

import com.keylesspalace.tusky.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.viewAccount
import com.keylesspalace.tusky.util.viewHashtag
import kotlin.properties.Delegates


class MathJaxView : WebView {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    @SuppressLint("SetJavaScriptEnabled")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setBackgroundColor(Color.TRANSPARENT)
        val typedValue = TypedValue()
        val theme: Resources.Theme = context.theme
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        @ColorInt val color = typedValue.data
//        val colorInt = ContextCompat.getColor(context, android.R.attr.textColorPrimary)
        textColor = String.format("#%06x", (0xFFFFFF and color))
        Log.e("Bonnjalal ", "textColor : $textColor / $color")

        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        with(settings) {
            loadWithOverviewMode = true
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (attrs != null) {
            val math = context.obtainStyledAttributes(attrs, R.styleable.MathJaxView)
            if (math.hasValue(R.styleable.MathJaxView_text)) {
                this.text = math.getString(R.styleable.MathJaxView_text)
            }
            if (math.hasValue(R.styleable.MathJaxView_textColor)){
                this.textColor = math.getString(R.styleable.MathJaxView_textColor)
            }
//            if (math.hasValue(R.styleable.MathJaxView_mathBackgroundColor)){
//                this.mathBackgroundColor = math.getString(R.styleable.MathJaxView_mathBackgroundColor)
//            }
            math.recycle()
        }

        isLongClickable = false
        setOnLongClickListener { true}
//        addJavascriptInterface(WebAppInterface(context), "LinkTag")
    }

    var text: String? by Delegates.observable("") { _, old, new ->
        if (old != new) {
            doRender()
        }
    }

    var textAlignment: TextAlign by Delegates.observable(TextAlign.START) { _, old, new ->
        if (old != new) {
            doRender()
        }
    }

    var textColor: String? by Delegates.observable("#FFFFFF") { _, old, new ->
        if (old != new) {
            doRender()
        }
    }

//    var mathBackgroundColor: String? by Delegates.observable("#00FFFFFF") { _, old, new ->
//        if (old != new) {
//            doRender()
//        }
//    }

    fun addJavascriptInterface(listener: StatusActionListener){
        addJavascriptInterface(WebAppInterface(context, listener), "LinkTag")
    }

    private fun doRender(){
        val p1 = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width\">"+
                "<title>MathJax TeX Test Page</title>\n" +
//                "<script src=\"https://polyfill.io/v3/polyfill.min.js?features=es6\"></script>\n" +
                "<script type=\"text/javascript\" id=\"MathJax-script\" async\n" +
                "  src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js\">\n" +
                "</script>\n" +"<script>"+
                "MathJax = {\n" +
                "  options: {\n" +
                "    enableMenu: false" + "}\n" +
                "};"+
                "</script>"+
                "<script type=\"text/javascript\">"+
                "function openAccount(tag) {"+
                    "LinkTag.openTagAccount(tag);"+
                "}" +
                "function openHashtag(tag) {"+
                "LinkTag.openHashtag(tag);"+
                "}"+
                "function onBodyClicked(position) {"+
                "LinkTag.onBodyClicked(position);"+
                "}"+
                "function stopPropagation(){"+
                "window.event.stopPropagation();"+
                "}"+
                "</script>"+
                "<style type=\"text/css\">body{color: ${textColor!!.lowercase()}; }</style>"+
                "<style type=\"text/css\">\n" +
                "a:link {\n" +
                "  color:SteelBlue;" +
                "  text-decoration: none;" +
                "}\n" +
                "a:visited {\n" +
                "  color:SteelBlue;" +
                "  text-decoration: none;" +
                "}\n" +
                "a:hover {\n" +
                "  color:RoyalBlue;" +
                "  text-decoration: none;" +
                "}"+
//                ".int {\n" +
//                "  position:relative;" +
//                "  z-index:999;" +
//                "}"+
//                ".ext {\n" +
//                "  position:absolute;" +
//                "  top:0;" +
//                "  left:0;" +
//                "  right:0;" +
//                "  bottom:0;" +
//                "  z-index:0;" +
//                "}\n" +
//                "      .link {" +
//                "        position: absolute;" +
//                "        width: 100%;" +
//                "        height: 100%;" +
//                "        top: 0;" +
//                "        left: 0;" +
//                "        z-index: 1;" +
//                "      }" +
                "</style>"+
                "</head>\n" //+
//                "<body>" //+
//                "<p style=\"text-align:" +
//                textAlignment.toString().lowercase() +
//                ";" + "color:" + textColor.toString().uppercase() + ";\">"
        val p2 = /*"</p>" +*/ "</body>\n" +
                "</html>"
        val fullMathText = p1 + text + p2
        val base64 = Base64.encodeToString(fullMathText.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
        render(base64)

//        style=\"background-color:" +
//        mathBackgroundColor.toString().uppercase() + ";\"
    }
    private fun render(base64: String) = loadUrl("data:text/html;charset=utf-8;base64,$base64")
}

enum class TextAlign {
    CENTER, START, END
}
/** Instantiate the interface and set the context  */
class WebAppInterface(private val mContext: Context, private val listener: StatusActionListener) {

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun openTagAccount(id: String) {
//        mContext.viewAccount(id)
        listener.onViewAccount(id)

//        Toast.makeText(mContext, id, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun openHashtag(tag: String) {
//        mContext.viewHashtag(tag)
        listener.onViewTag(tag)
//        Toast.makeText(mContext, tag, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun onBodyClicked(position: Int){
        listener.onViewThread(position)
        Toast.makeText(mContext, "position $position", Toast.LENGTH_SHORT).show()
    }
}