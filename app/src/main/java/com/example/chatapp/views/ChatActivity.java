package com.example.chatapp.views;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.viewmodel.MyViewModel;
import com.example.chatapp.views.Adapter.ChatAdapter;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private static final String FCM_PREFS = "FCM_Prefs";
    private static final String FCM_TOKEN_KEY = "fcm_token";
    private static final String CHANNEL_ID = "chat_notifications";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;

    private ActivityChatBinding binding;
    private MyViewModel myViewModel;
    private ChatAdapter myAdapter;
    private RecyclerView recyclerView;
    private List<ChatMessage> messageList = new ArrayList<>();
    private String groupName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_chat);

        // Initialize notification channel
        //createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chat Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // Check and request notification permission
        //handleNotificationPermissions();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkNotificationPermission()) {
                requestNotificationPermission();
            }
        }

        // Initialize components
        //initializeComponents();
        myViewModel = new ViewModelProvider(this).get(MyViewModel.class);
        groupName = getIntent().getStringExtra("GROUP_NAME");

        recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        binding.setVModel(myViewModel);

        // Set up observers and listeners
       // setupMessageObserver();
        myViewModel.getMessageLiveData(groupName).observe(this, chatMessages -> {
            messageList.clear();
            messageList.addAll(chatMessages);

            if (myAdapter == null) {
                myAdapter = new ChatAdapter(messageList, getApplicationContext());
                recyclerView.setAdapter(myAdapter);
            } else {
                myAdapter.notifyDataSetChanged();
            }

            if (!messageList.isEmpty()) {
                recyclerView.smoothScrollToPosition(messageList.size() - 1);
            }

            // Show notification for new messages
            if (!chatMessages.isEmpty()) {
                showMessageNotification(chatMessages.get(chatMessages.size() - 1));
            }
        });
        //setupSendButton();
        binding.sendBTN.setOnClickListener(view -> {
            String msg = binding.edittextChatMessage.getText().toString();
            if (!msg.trim().isEmpty()) {
                myViewModel.sendMessage(msg, groupName);
                binding.edittextChatMessage.getText().clear();
            }
        });

        // Handle FCM operations
        //handleFcmOperations();
        String topic = groupName.replaceAll(" ", "_");
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener(task -> {
                    String logMsg = task.isSuccessful()
                            ? "Subscribed to topic: " + topic
                            : "Subscription failed";
                    Log.d(TAG, logMsg);
                });

        // Verify and handle FCM token
        verifyFcmToken();
    }



    private boolean checkNotificationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_CODE
        );
    }


    private void showMessageNotification(ChatMessage message) {
        if (message.isMine() || !checkNotificationPermission() || isAppInForeground()) {
            return;
        }

        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_ic_notification)
                    .setContentTitle("New message in " + groupName)
                    .setContentText(message.getText())
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission denied", e);
        }
    }



    private void verifyFcmToken() {
        SharedPreferences prefs = getSharedPreferences(FCM_PREFS, MODE_PRIVATE);
        String savedToken = prefs.getString(FCM_TOKEN_KEY, null);

        if (savedToken != null) {
            Log.d(TAG, "Using saved FCM token: " + savedToken);
        } else {
            fetchNewFcmToken();
        }
    }

    private void fetchNewFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "FCM token fetch failed", task.getException());
                        Toast.makeText(this, "Failed to get FCM token", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);

                    getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(FCM_TOKEN_KEY, token)
                            .apply();
                });
    }

    private boolean isAppInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return false;

        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.processName.equals(getPackageName()) &&
                    process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupName != null) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(groupName.replaceAll(" ", "_"));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (groupName != null) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(groupName.replaceAll(" ", "_"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }
}