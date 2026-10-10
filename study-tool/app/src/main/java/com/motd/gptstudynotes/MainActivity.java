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
 String m=getContentResolver().getType(u);if(m==null)m="";boolean pdf="application/pdf".equals(m)||name.toLowerCase().endsWith(".pdf");
 boolean img="image/png".equals(m)||"image/jpeg".equals(m)||"image/webp".equals(m);
 if(!pdf&&!img){js("failed","請選擇 PDF、JPG、PNG 或 WEBP");return;}
 if(size>MAX_BYTES){js("failed","檔案過大（上限 15 MB）");return;}
 file=u;filename=name;mime=pdf?"application/pdf":m;js("selected",name);
 }
 private byte[] read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>MAX_BYTES)throw new Exception("檔案超過 15 MB");}return out.toByteArray();}}
 private void analyze(String key,String mode){if(file==null){js("failed","請先選擇檔案");return;}if(!key.startsWith("sk-")){js("failed","API Key 格式不正確");return;}
 final Uri selectedFile=file;final String selectedName=filename, selectedMime=mime;
 new Thread(()->{HttpURLConnection conn=null;try{
 byte[] bytes=read(selectedFile);String b64=Base64.encodeToString(bytes,Base64.NO_WRAP);
 String prompt="你是嚴謹的繁體中文學習筆記助理。只依據使用者附上的檔案整理，不可杜撰、不可加進未經檔案證實的內容。請保留原資料的大標題、術語與知識深度。遇到內容無法辨識時明確標示。";
 if("exam".equals(mode))prompt+=" 輸出：一、必背重點；二、關鍵定義與比較；三、易錯觀念；四、五題練習題與解答。";
 else if("full".equals(mode))prompt+=" 輸出：一、文件概述；二、依章節分層整理；三、重要名詞解釋；四、重點比較表（純文字）；五、總複習。";
 else prompt+=" 輸出：一、3句總覽；二、5到10條精簡要點；三、重要名詞；四、考前30秒速記。";
 JSONArray content=new JSONArray();content.put(new JSONObject().put("type","input_text").put("text",prompt));
 String dataUri="data:"+selectedMime+";base64,"+b64;
 if("application/pdf".equals(selectedMime))content.put(new JSONObject().put("type","input_file").put("filename",selectedName).put("file_data",dataUri));
 else content.put(new JSONObject().put("type","input_image").put("image_url",dataUri));
 JSONObject msg=new JSONObject().put("role","user").put("content",content);
 JSONObject body=new JSONObject().put("model","gpt-4.1-mini").put("input",new JSONArray().put(msg)).put("max_output_tokens",3500).put("store",false);
 conn=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
 conn.setRequestMethod("POST");conn.setConnectTimeout(30000);conn.setReadTimeout(180000);conn.setDoOutput(true);
 conn.setRequestProperty("Authorization","Bearer "+key);conn.setRequestProperty("Content-Type","application/json");
 try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
 int code=conn.getResponseCode();InputStream response=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
 String raw;try(InputStream stream=response;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);raw=out.toString("UTF-8");}
 JSONObject data=new JSONObject(raw);if(code<200||code>=300){JSONObject e=data.optJSONObject("error");throw new Exception((e!=null?e.optString("message"):raw));}
 StringBuilder text=new StringBuilder();JSONArray output=data.optJSONArray("output");if(output!=null)for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null||!"message".equals(item.optString("type")))continue;JSONArray parts=item.optJSONArray("content");if(parts!=null)for(int j=0;j<parts.length();j++){JSONObject part=parts.optJSONObject(j);if(part!=null&&"output_text".equals(part.optString("type")))text.append(part.optString("text")).append("\n");}}
 if(text.length()==0)throw new Exception("沒有取得文字結果，請重試或更換文件");js("finished",text.toString());
 }catch(Exception e){js("failed",e.getMessage()==null?"未知錯誤":e.getMessage());}finally{if(conn!=null)conn.disconnect();}
 }).start();
 }
 public class Bridge {
  @JavascriptInterface public void pickFile(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png","image/webp"});startActivityForResult(i,PICK);});}
  @JavascriptInterface public void analyze(String key,String mode){MainActivity.this.analyze(key,mode);}
  @JavascriptInterface public void copy(String data){runOnUiThread(()->{ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("GPT Study Notes",data));});}
  @JavascriptInterface public void share(String data){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,data);startActivity(Intent.createChooser(i,"分享學習筆記"));});}
 }
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("StudyAndroid");web.destroy();}super.onDestroy();}
}
