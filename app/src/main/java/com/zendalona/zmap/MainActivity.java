package com.zendalona.zmap;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.GetTokenResult;

public class MainActivity extends AppCompatActivity {
    FirebaseAuth auth;
    GoogleSignInClient googleSignInClient;
    WebView webView;
    private FirebaseUser currentUser;

    private static final String WEB_APP_URL = "http://192.168.1.32:3000/";


    private final ActivityResultLauncher<Intent> activityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> signInTask = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = signInTask.getResult(ApiException.class);
                        if (account != null) {
                            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                            auth.signInWithCredential(credential)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(MainActivity.this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
                                            currentUser = auth.getCurrentUser();
                                            pushTokenToWebView();
                                        } else {
                                            Toast.makeText(MainActivity.this, "Firebase Sign In Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                            currentUser = null;
                                            pushTokenToWebView();
                                        }
                                    });
                        }
                    } catch (ApiException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Google Sign In Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        currentUser = null;
                        pushTokenToWebView();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Google Sign In Cancelled", Toast.LENGTH_SHORT).show();
                    currentUser = null;
                    pushTokenToWebView();
                }
            });


    public class WebAppInterface {
        MainActivity mContext;

        WebAppInterface(MainActivity c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showToast(String toast) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void startNativeGoogleSignIn() {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = googleSignInClient.getSignInIntent();
                activityResultLauncher.launch(intent);
            });
        }

        @JavascriptInterface
        public void startNativeGoogleSignOut() {
            new Handler(Looper.getMainLooper()).post(() -> {
                FirebaseAuth.getInstance().signOut();
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    Toast.makeText(mContext, "Signed out successfully!", Toast.LENGTH_SHORT).show();
                    currentUser = null;
                    pushTokenToWebView();
                });
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        // WebView setup
        webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pushTokenToWebView();
            }
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.loadUrl(WEB_APP_URL);

        // Google Sign-In setup
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id)) // <-- Web client ID from Firebase Console
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(MainActivity.this, options);
    }

    private void pushTokenToWebView() {
        if (currentUser != null) {
            currentUser.getIdToken(false)
                    .addOnCompleteListener(task -> runOnUiThread(() -> {
                        if (task.isSuccessful()) {
                            String idToken = task.getResult().getToken();
                            // Send this token to your backend endpoint to exchange for a Firebase Custom Token
                            String jsCode = "javascript:receiveFirebaseToken('" + idToken + "');";
                            webView.evaluateJavascript(jsCode, null);
                        } else {
                            webView.evaluateJavascript("javascript:receiveFirebaseToken(null);", null);
                            Toast.makeText(MainActivity.this, "Failed to get Firebase token: " + task.getException(), Toast.LENGTH_SHORT).show();
                        }
                    }));
        } else {
            runOnUiThread(() ->
                    webView.evaluateJavascript("javascript:receiveFirebaseToken(null);", null));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
