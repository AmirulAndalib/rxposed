package hepta.rxposed.manager.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import hepta.rxposed.manager.BuildConfig;
import hepta.rxposed.manager.RxposedApp;

public  class InjectTool {

    // Used to load the 'injecttool' library on application startup.

    /**
     * A native method that is implemented by the 'injecttool' native library,
     * which is packaged with this application.
     */

    public static String su_path;
    public static String arm64_so = "arm64_lib"+ BuildConfig.Rxposed_Inject_So+".so";
    public static String armv7_so = "armv7_lib"+ BuildConfig.Rxposed_Inject_So+".so";
    public static String arm64_InjectTool = "arm64_"+"InjectTool";
    public static String armv7_InjectTool = "armv7_"+"InjectTool";
    public static String HostName = BuildConfig.APPLICATION_ID;
    public static String HostProviderName = BuildConfig.APPLICATION_ID+".Provider";

    public static int arch_armv7=32;
    public static int arch_armv8=64;
    public static String InjectArg = HostName+":"+HostProviderName;
    public static Context context = null;
    private static String InjectTool_arm64_path;
    private static String InjectTool_armv7_path;
    private static String InjectSo_arm64_path;
    private static String InjectSo_armv7_path;

    public static void zygote_reboot() throws IOException {
        rootRun("killall zygote");
    }


    public static void init(){
        context = RxposedApp.getInstance().getApplicationContext();
        InjectTool.su_path = context.getSharedPreferences("rxposed",MODE_PRIVATE).getString("supath","su");

        InjectTool_arm64_path = context.getFilesDir().getAbsolutePath()+ File.separator+arm64_InjectTool;
        InjectTool_armv7_path = context.getFilesDir().getAbsolutePath()+ File.separator+armv7_InjectTool;
        InjectSo_arm64_path   = context.getFilesDir().getAbsolutePath()+ File.separator+arm64_so;
        InjectSo_armv7_path   = context.getFilesDir().getAbsolutePath()+ File.separator+armv7_so;

        try {
            InjectTool.copyAssetToDst(context,arm64_InjectTool,InjectTool_arm64_path);
            Runtime.getRuntime().exec("chmod +x "+InjectTool_arm64_path);
            InjectTool.copyAssetToDst(context,armv7_InjectTool,InjectTool_armv7_path);
            Runtime.getRuntime().exec("chmod +x "+InjectTool_armv7_path);
            InjectTool.copyAssetToDst(context,arm64_so,InjectSo_arm64_path);
            InjectTool.copyAssetToDst(context,armv7_so,InjectSo_armv7_path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void Application_reboot() throws IOException {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //与正常页面跳转一样可传递序列化数据,在Launch页面内获得
        intent.putExtra("REBOOT", "reboot");
        context.startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    public static boolean zygote_patrace() throws IOException {
        int uid = context.getApplicationInfo().uid;

        //修改函数或者参数，记得修改符号
//        String cmd = InjectTool_arm64_path+" -n zygote64 -so "+ injectSo+" -symbols _Z9dobby_strPKc com.rxposed.qmulkt:com.rxposed.qmulkt.Provider";

        String cmd_arm64 = InjectTool_arm64_path+" -n zygote64 -so "+ InjectSo_arm64_path+" -symbols _Z14Ptrace_ZygotesPKc "+uid+":"+InjectArg;
//        String cmd_armv7 = InjectTool_armv7_path+" -n zygote -so "  + InjectSo_armv7_path+" -symbols _Z9dobby_strPKc "+InjectArg;
        Log.d("rzx",cmd_arm64);
//        Log.d("rzx",cmd_armv7);
//        Runtime.getRuntime().exec("su "+cmd_arm64);

//        rootRun("su","top");
        rootRun(cmd_arm64);
//        rootRun(cmd_armv7);
        return  true;
    }


    // /data/user/0/hepta.rxposed.manager/files/arm64_InjectTool -p 4903 -so /data/user/0/hepta.rxposed.manager/files/arm64_libnativeloader.so -symbols _Z9Inject_ProcessPKc hepta.rxposed.manager.Provider;com.hep>                                                     <
    public static void Inject_Process(int Pid,String package_list)  {

        String Inject_Arg = HostProviderName+":"+package_list;
        int arch = ByPidGetProcessArch(Pid);
        Log.e("Rzx","inject arch:"+arch);
        if(arch == arch_armv8){

            String cmd_arm64 = InjectTool_arm64_path+" -p "+Pid+" -so "+ InjectSo_arm64_path+" -symbols _Z14Inject_PorcessPKc "+Inject_Arg;;
            Log.e("Rzx 64",cmd_arm64);
            rootRun(cmd_arm64);
        }else {

            String cmd_armv7 = InjectTool_armv7_path+" -p "+Pid+" -so "+ InjectSo_armv7_path+" -symbols _Z14Inject_PorcessPKc "+Inject_Arg;
            Log.e("Rzx 32",cmd_armv7);
            rootRun(cmd_armv7);
        }

    }

    private static int ByPidGetProcessArch(int pid) {
        String exe = rootRun("file -L /proc/"+pid+"/exe");
        if(exe.contains("linker64")){
            return 64;
        }else {
            return 32;
        }
    }


    public static boolean copyAssetToDst(Context context, String fileName,String dstFilePath){
        try {
            File outFile =new File(dstFilePath);
            if (!outFile.exists()){
                boolean res=outFile.createNewFile();
                if (!res){
                    return false;
                }
            }else {
                if (outFile.length()>10){//表示已经写入一次
                    outFile.delete();
                }
            }
            InputStream is=context.getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();
            is.close();
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }


    public static String shell(String cmd){
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = "";
            while (true) {
                if ((line = reader.readLine()) == null) break;
                return line;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }


    public static String rootRun(String cmd)
    {

        String Result  ="";
        try {
            // 申请获取root权限
            Process process = Runtime.getRuntime().exec(su_path); //"/system/xbin/su"
            // 获取输出流
            OutputStream outputStream = process.getOutputStream();
            InputStream is = process.getInputStream();
            InputStream es = process.getErrorStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
            dataOutputStream.writeBytes(cmd);
            dataOutputStream.flush();
            dataOutputStream.close();
            outputStream.close();
            int code = process.waitFor();
            String is_line = null;
            String es_line = null;
//            Log.d("InjectTool", "Run:\"" + cmd +"\", "+"process.waitFor() = " + code);
            BufferedReader br;
            br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            while ((is_line = br.readLine()) != null) {
                Log.d("InjectTool is ", is_line);
                Result = Result+is_line+"\n";
            }

            br = new BufferedReader(new InputStreamReader(es, "UTF-8"));
            while ((es_line = br.readLine()) != null) {
//                Log.e("InjectTool es", es_line);
//                Result += es_line;

            }

        } catch (Throwable t) {
//            Log.e("InjectTool", "Throwable = " + t.getMessage());
            t.printStackTrace();
        }
        return Result;
    }

}