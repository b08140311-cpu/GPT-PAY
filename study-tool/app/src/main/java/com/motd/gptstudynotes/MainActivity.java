package com.motd.gptstudynotes;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
 private WebView web; private Uri file; private String filename; private String mime; private static final int PICK=9;
 private static final int MAX_BYTES=15*1024*1024;
 @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(0xff080e1c);getWindow().setNavigationBarColor(0xff080e1c);
 web=new WebView(this);web.setBackgroundColor(0xff080e1c);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
 web.addJavascriptInterface(new Bridge(),"StudyAndroid");web.setWebViewClient(new WebViewClient());setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 private void js(String function,String value){runOnUiThread(()->web.evaluateJavascript(function+"("+JSONObject.quote(value)+")",null));}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;
 Uri u=data.getData();if(u==null)return; String name="document";long size=-1;try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int a=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),z=c.getColumnIndex(OpenableColumns.SIZE);if(a>=0)name=c.getString(a);if(z>=0&&!c.isNull(z))size=c.getLong(z);}}
 String m=getContentResolver().getType(u);if(m==null)m="";String lower=name.toLowerCase(java.util.Locale.ROOT);boolean pdf="application/pdf".equals(m)||lower.endsWith(".pdf");
 boolean img="image/png".equals(m)||"image/jpeg".equals(m)||"image/webp".equals(m);boolean textFile=lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".csv")||lower.endsWith(".docx")||lower.endsWith(".pptx");
 if(!pdf&&!img&&!textFile){js("failed","請選擇 PDF、圖片或 TXT/MD/CSV/DOCX/PPTX");return;}
 if(size>MAX_BYTES){js("failed","檔案過大（上限 15 MB）");return;}
 file=u;filename=name;mime=pdf?"application/pdf":(img?m:(!m.isEmpty()?m:"application/octet-stream"));js("selected",name);
 }
 private byte[] read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>MAX_BYTES)throw new Exception("檔案超過 15 MB");}return out.toByteArray();}}
 private void analyze(String key,String mode,String userText,String previousId){if((userText==null||userText.trim().isEmpty())&&file==null){js("failed","請輸入訊息或選擇檔案");return;}if(!key.startsWith("sk-")){js("failed","API Key 格式不正確");return;}
 final Uri selectedFile=file;final String selectedName=filename, selectedMime=mime;
 new Thread(()->{HttpURLConnection conn=null;try{

 JSONArray content=new JSONArray();
 String prompt="你是 GPT Study Chat，使用繁體中文，簡潔清楚但可完整回答。使用者上傳教材時以檔案內容為依據；若資料不清楚，要標記不能確認之處。可以與使用者持續對話，解釋課程觀念、翻譯並整理筆記。";
 if("exam".equals(mode))prompt+=" 若要求整理，優先整理必背概念、容易混淆處及練習題。";
 else if("full".equals(mode))prompt+=" 若要求整理，按章節列出標題、概念、重要細節及對照。";
 else prompt+=" 若要求整理，簡短列出核心重點、術語及速記。";
 String actual=(userText==null?"":userText.trim());
 if(selectedFile!=null&&actual.length()==0)actual="請幫我整理附件重點，按適合課堂學習的方式分段。";
 content.put(new JSONObject().put("type","input_text").put("text",actual));
 if(selectedFile!=null) {
   byte[] bytes=read(selectedFile);
   String dataUri="data:"+selectedMime+";base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP);
   if("application/pdf".equals(selectedMime)||!selectedMime.startsWith("image/"))
     content.put(new JSONObject().put("type","input_file").put("filename",selectedName).put("file_data",dataUri));
   else content.put(new JSONObject().put("type","input_image").put("image_url",dataUri));
 }
 JSONObject msg=new JSONObject().put("role","user").put("content",content);
 JSONObject body=new JSONObject().put("model","gpt-4.1-mini").put("instructions",prompt).put("input",new JSONArray().put(msg)).put("max_output_tokens",4000).put("store",true);
 if(previousId!=null&&!previousId.isEmpty())body.put("previous_response_id",previousId);
 conn=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
 conn.setRequestMethod("POST");conn.setConnectTimeout(30000);conn.setReadTimeout(180000);conn.setDoOutput(true);
 conn.setRequestProperty("Authorization","Bearer "+key);conn.setRequestProperty("Content-Type","application/json");
 try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
 int code=conn.getResponseCode();InputStream response=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
 String raw;try(InputStream stream=response;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);raw=out.toString("UTF-8");}
 JSONObject data=new JSONObject(raw);if(code<200||code>=300){JSONObject e=data.optJSONObject("error");throw new Exception((e!=null?e.optString("message"):raw));}
 StringBuilder text=new StringBuilder();JSONArray output=data.optJSONArray("output");if(output!=null)for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null||!"message".equals(item.optString("type")))continue;JSONArray parts=item.optJSONArray("content");if(parts!=null)for(int j=0;j<parts.length();j++){JSONObject part=parts.optJSONObject(j);if(part!=null&&"output_text".equals(part.optString("type")))text.append(part.optString("text")).append("\n");}}
 if(text.length()==0)throw new Exception("沒有取得文字結果，請重試");String answer=text.toString();String replyId=data.optString("id","");runOnUiThread(()->{web.evaluateJavascript("window.chatFinished("+JSONObject.quote(answer)+","+JSONObject.quote(replyId)+")",null);});
 }catch(Exception e){js("failed",e.getMessage()==null?"未知錯誤":e.getMessage());}finally{if(conn!=null)conn.disconnect();}
 }).start();
 }
 public class Bridge {
  @JavascriptInterface public void pickFile(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png","image/webp","text/plain","text/markdown","text/csv","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.presentationml.presentation"});startActivityForResult(i,PICK);});}
  @JavascriptInterface public void analyze(String key,String mode,String message,String previousId){MainActivity.this.analyze(key,mode,message,previousId);}
  @JavascriptInterface public void copy(String data){runOnUiThread(()->{ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("GPT Study Notes",data));});}
  @JavascriptInterface public void share(String data){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,data);startActivity(Intent.createChooser(i,"分享學習筆記"));});}
 }
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("StudyAndroid");web.destroy();}super.onDestroy();}
}
