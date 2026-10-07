package com.project.vortex.client.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.security.MessageDigest;
import java.util.Locale;

public class HashCat {
    private static final String TAG = "McDeHasher";
    private static HashCat instance;

    
    public native String getSignaturesSha1(Context context);
    public native boolean checkSha1(Context context);
    public native String getToken(Context context, String userId);

    
    static {
        System.loadLibrary("native-lib");
    }

    
    public static synchronized HashCat getInstance() {
        if (instance == null) {
            instance = new HashCat();
        }
        return instance;
    }

    private HashCat() {
        
    }


    /**
     * Signature gate removed for the Vortex build.
     *
     * Upstream compared the APK signing certificate against a hardcoded SHA1
     * (valid.cpp) and, on mismatch, showed an empty toast, called finishAffinity(),
     * killed the process and called System.exit(0) - which looked exactly like an
     * instant crash for anyone running a self-built / re-signed APK.
     *
     * The check is now a no-op so the app simply starts.
     */
    public boolean LintHashInit(Context context) {
        Log.i(TAG, "Signature gate disabled (Vortex build)");
        return true;
    }

    public String getSha1Value(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(cert);
            StringBuilder hexString = new StringBuilder();

            for (byte b : publicKey) {
                String appendString = Integer.toHexString(0xFF & b).toUpperCase(Locale.US);
                if (appendString.length() == 1) {
                    hexString.append("0");
                }
                hexString.append(appendString);
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public String getTokenForUser(Context context, String userId) {
        return getToken(context, userId);
    }
}