package com.example.chatapp.Repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.chatapp.model.ChatGroup;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.views.GroupsActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Repository {
    MutableLiveData<List<ChatGroup>> chatGroupMutableLiveData;

    FirebaseDatabase database;
    DatabaseReference reference;
    DatabaseReference groupReference;
    MutableLiveData<List<ChatMessage>> messagesLiveData;

    public Repository() {
        this.chatGroupMutableLiveData = new MutableLiveData<>();
        database = FirebaseDatabase.getInstance();
        reference = database.getReference();
        messagesLiveData = new MutableLiveData<>();
    }

    public void firebaseAnonymousAuth(Context context){
        FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(task.isSuccessful()){
                    Intent i = new Intent(context, GroupsActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                }
            }
        });
    }

    public String getCurrentUserId(){
        return FirebaseAuth.getInstance().getUid();
    }

    public void signOUT() {
        FirebaseAuth.getInstance().signOut();
    }

    public MutableLiveData<List<ChatGroup>> getChatGroupMutableLiveData() {
        List<ChatGroup> groupList = new ArrayList<>();

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                groupList.clear();
                for(DataSnapshot dataSnapshot: snapshot.getChildren()){
                    ChatGroup group = new ChatGroup(dataSnapshot.getKey());
                    groupList.add(group);
                    chatGroupMutableLiveData.postValue(groupList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    return chatGroupMutableLiveData;
    }

    public void createNewChatGroup(String groupName){
        reference.child(groupName).setValue(groupName);
    }

    public MutableLiveData<List<ChatMessage>> getMessagesLiveData(String groupName) {
        groupReference = database.getReference().child(groupName);

        List<ChatMessage> messageList = new ArrayList<>();
        groupReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for(DataSnapshot dataSnapshot : snapshot.getChildren()){
                    ChatMessage message = dataSnapshot.getValue(ChatMessage.class);
                    messageList.add(message);
                    messagesLiveData.postValue(messageList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        return messagesLiveData;
    }

    public void sendMessage(String messageText, String chatGroup){

        DatabaseReference ref = database
                .getReference(chatGroup);

        if (!messageText.trim().equals("")) {
            String senderId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            ChatMessage msg = new ChatMessage(
                    senderId,
                    messageText,
                    System.currentTimeMillis()
            );

            String randomKey = ref.push().getKey();
            ref.child(randomKey).setValue(msg);

            // Send notification to all group members
            sendNotificationToTopic(chatGroup, messageText, senderId);

        }
    }

    public void sendNotificationToTopic(String groupName, String messageText, String senderId) {
        // The topic name can be the group name (replace spaces with underscores)
        String topic = groupName.replaceAll(" ", "_");

        // Send a notification to everyone subscribed to this topic
        FirebaseMessaging.getInstance().send(new RemoteMessage.Builder(senderId + "@fcm.googleapis.com")
                .setMessageId(Integer.toString(new Random().nextInt(100000)))
                .addData("title", "New message in " + groupName)
                .addData("message", messageText)
                .addData("groupName", groupName)
                .setTtl(3600)
                .build());
    }


}
