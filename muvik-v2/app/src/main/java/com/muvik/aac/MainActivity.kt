package com.muvik.aac
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.util.Base64
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
 private lateinit var web: WebView
 private var tts: TextToSpeech?=null
 private var ready=false
 private var chooser: ValueCallback<Array<Uri>>?=null
 private val request=4102
 private val backupRequest=4103
 private var pendingBackup:ByteArray?=null
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
  tts=TextToSpeech(this,this)
  web=WebView(this)
  web.settings.javaScriptEnabled=true
  web.settings.domStorageEnabled=true
  web.settings.allowFileAccess=true
  web.settings.allowContentAccess=true
  web.webViewClient=WebViewClient()
  web.webChromeClient=object:WebChromeClient(){
   override fun onShowFileChooser(v:WebView,cb:ValueCallback<Array<Uri>>,p:FileChooserParams):Boolean{
    chooser?.onReceiveValue(null);chooser=cb
    return try{
     val types=p.acceptTypes.filter{it.isNotBlank()}.flatMap{it.split(",")}.distinct().toTypedArray()
     val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
      addCategory(Intent.CATEGORY_OPENABLE)
      type=if(types.size==1)types[0] else "*/*"
      if(types.size>1)putExtra(Intent.EXTRA_MIME_TYPES,types)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
     }
     startActivityForResult(intent,request);true
    }catch(e:Exception){chooser=null;false}
   }
  }
  web.addJavascriptInterface(Voice(),"AndroidTTS")
  setContentView(web)
  web.loadUrl("file:///android_asset/index.html")
 }
 @Deprecated("Deprecated in Android API")
 override fun onActivityResult(code:Int,result:Int,data:Intent?){
  super.onActivityResult(code,result,data)
  if(code==backupRequest){
   val uri=if(result==Activity.RESULT_OK)data?.data else null
   if(uri!=null){try{contentResolver.openOutputStream(uri)?.use{it.write(pendingBackup?:byteArrayOf())};web.evaluateJavascript("toast('Резервну копію збережено')",null)}catch(e:Exception){web.evaluateJavascript("toast('Не вдалося зберегти копію')",null)}}
   pendingBackup=null;return
  }
  if(code==request){
   val uri=if(result==Activity.RESULT_OK)data?.data else null
   if(uri!=null){try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}}
   chooser?.onReceiveValue(if(uri!=null)arrayOf(uri) else null);chooser=null
  }
 }
 override fun onInit(status:Int){ready=status==TextToSpeech.SUCCESS;if(ready)tts?.language=Locale.forLanguageTag("uk-UA")}
 inner class Voice{
  @JavascriptInterface fun speak(text:String,language:String,pitch:String,rate:String){
   runOnUiThread{
    val engine=tts?:return@runOnUiThread
    if(!ready||text.isBlank())return@runOnUiThread
    val tag=when(language){"uk"->"uk-UA";"pl"->"pl-PL";"en"->"en-US";"de"->"de-DE";"fr"->"fr-FR";"es"->"es-ES";"it"->"it-IT";"cs"->"cs-CZ";"sk"->"sk-SK";else->language}
    val result=engine.setLanguage(Locale.forLanguageTag(tag))
    if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED)engine.setLanguage(Locale.getDefault())
    engine.setPitch(pitch.toFloatOrNull()?.coerceIn(.5f,2f)?:1f)
    engine.setSpeechRate(rate.toFloatOrNull()?.coerceIn(.6f,1.4f)?:1f)
    engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"muvik")
   }
  }
  @JavascriptInterface fun saveBackup(encoded:String){
   try{val bytes=Base64.decode(encoded,Base64.DEFAULT);runOnUiThread{pendingBackup=bytes;val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"Muvik-backup.json")};startActivityForResult(intent,backupRequest)}}catch(e:Exception){runOnUiThread{web.evaluateJavascript("toast('Не вдалося створити копію')",null)}}
  }
  @JavascriptInterface fun installVoiceData(){runOnUiThread{try{startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))}catch(e:Exception){web.evaluateJavascript("toast('Відкрий налаштування Android: Синтез мовлення')",null)}}}
  @JavascriptInterface fun exitApp(){runOnUiThread{finish()}}
 }
 @Deprecated("Deprecated in Android API")
 override fun onBackPressed(){if(::web.isInitialized)web.evaluateJavascript("handleSystemBack()",null)else super.onBackPressed()}
 override fun onDestroy(){if(::web.isInitialized)web.destroy();tts?.stop();tts?.shutdown();super.onDestroy()}
}