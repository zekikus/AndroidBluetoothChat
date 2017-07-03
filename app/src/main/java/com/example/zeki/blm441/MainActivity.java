package com.example.zeki.blm441;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    private TextView status;
    private Button connectBtn;
    private ListView listView;
    private EditText inputLayout;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;
    private BluetoothAdapter bluetoothAdapter;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private ChatServer chatServer;
    private BluetoothDevice connectingDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_screen);
        initUIElement();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth özelliği bulunmamaktadır.!", Toast.LENGTH_SHORT).show();
            finish();
        }

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDialogScreen();
            }
        });

        //Gönderilen ve alınan mesajların tutulduğu liste
        chatMessages = new ArrayList<>();

        //Gönderilen ve alınan mesajların listviewe basılmak üzere tutulduğu adapter
        chatAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, chatMessages){
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position,convertView,parent);

                int textColor = (textView.getText().toString().contains("Ben:")) ? R.color.colorPrimary : R.color.colorAccent;
                textView.setTextColor(getResources().getColor(textColor));

                return textView;
            }
        };

        listView.setAdapter(chatAdapter);
    }

    /*
        ChatServer'ı dinleyerek oluşan durum değişikliklerini takip eder.
        1.Mesaj göndermek için eşleşmek istenen cihaz bağlı mı?
        2.Mesajlaşılan cihazdan gelen mesaj var mı?
        3.ChatServer üzerinden mesaj gönderiliyor mu?
     */
    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatServer.STATE_CONNECTED:
                            setStatus("Bağlanılan Cihaz: " + connectingDevice.getName());
                            connectBtn.setEnabled(false);
                            break;
                        case ChatServer.STATE_CONNECTING:
                            setStatus("Bağlanıyor...");
                            connectBtn.setEnabled(false);
                            break;
                        case ChatServer.STATE_LISTEN:
                        case ChatServer.STATE_NONE:
                            setStatus("Bağlı Cihaz Bulunamadı...");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    chatMessages.add("Ben: " + writeMessage);
                    //ChatAdaptera abone olan elementlere değişiklik olduğunu bildirir ve elementler kendilerini günceller.
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatMessages.add(connectingDevice.getName() + ":  " + readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Bağlanılan Cihaz: " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void openDialogScreen() {

        DialogScreen screen = new DialogScreen(chatServer,bluetoothAdapter,this);
        screen.initialScreen();
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void initUIElement() {
        status = (TextView) findViewById(R.id.status);
        connectBtn = (Button) findViewById(R.id.btn_connect);
        listView = (ListView) findViewById(R.id.list);
        inputLayout = (EditText) findViewById(R.id.input_layout);
        View sendBtn = findViewById(R.id.btn_send);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inputLayout.getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this, "Boş mesaj gönderilemez", Toast.LENGTH_SHORT).show();
                } else {
                    sendMessage(inputLayout.getText().toString());
                    inputLayout.setText("");
                }
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    chatServer = new ChatServer(this, handler);
                } else {
                    Toast.makeText(this, "Bluetooth erişimine izin verilmedi. Uygulama Kapatılıyor.", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    //Mesajı Chat Servera Gönderir.
    private void sendMessage(String message) {
        if (chatServer.getState() != ChatServer.STATE_CONNECTED) {
            Toast.makeText(this, "Bağlantı Kayboldu.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatServer.write(send);
        }
    }

    //Uygulama Başladığında Bluetooth Otomatik olarak açılır
    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            chatServer = new ChatServer(this, handler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (chatServer != null) {
            if (chatServer.getState() == ChatServer.STATE_NONE) {
                chatServer.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatServer != null)
            chatServer.stop();
    }



}
