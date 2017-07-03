package com.example.zeki.blm441;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Zeki on 28.11.2016.
 */

public class ChatServer {

    private static final String APP_NAME = "BluetoothChatApp";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ReadWriteThread connectedThread;
    private int state;

    static final int STATE_NONE = 0;
    static final int STATE_LISTEN = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;

    public ChatServer(Context context, Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;

        this.handler = handler;
    }

    // Sohbet Durumunun Güncellendiği Bölüm (LISTEN, CONNECTING, CONNECTED)
    private synchronized void setState(int state) {
        this.state = state;

        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // Sohbet Durumu Bilgisinin Alındığı Bölüm
    public synchronized int getState() {
        return state;
    }


    public synchronized void start() {
        // ConnectThread varsa başlangıç için sıfırla
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // ConnectedThread varsa başlangıç için sıfırla
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        //Serveri Dinleme Konumuna Getir. Bluetooth servisi olan cihazlarla eşleşmek, keşfetmek için
        setState(STATE_LISTEN);
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    // Diğer cihaza bağlantıyı başlat
    public synchronized void connect(BluetoothDevice device) {
        // CONNECTING durumunda thread varsa sıfırla
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        //Thread varsa boşalt
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Adresi gelen cihazla bağlantı kurmak için yeni thread başlatır.
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    // Bağlantıları yönetir.
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

        //Threadleri Boşaltır.
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }


        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Mesaj gönderimi ve alımı için ilgili threadi başlatır.
        connectedThread = new ReadWriteThread(socket);
        connectedThread.start();

        // Bağlantı bilgisinden gelen cihaz adını MainActivity sınıfına gönderir.
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(MainActivity.DEVICE_OBJECT, device);
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    // Bütün threadler durdurulur.
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }

    //Durum Connected değil ise bağlı durumda cihaz yoksa yazma işlemi gerçekleşmez.
    //Bağlı durumda bir cihaz varsa Okuma Yazma işlemlerinin gerçekleştirildiği threade, sokete yazılmak üzere mesaj gönderilir.
    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = connectedThread;
        }
        r.write(out);
    }

    private void connectionFailed() {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Bağlı Cihaz Bulunamadı");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // ChatServeri tekrar dinleme moduna al yeni bir cihazla bağlantı kurulana kadar
        ChatServer.this.start();
    }

    private void connectionLost() {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Cihaz ile Bağlantı Koptu");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // ChatServeri tekrar dinleme moduna al yeni bir cihazla bağlantı kurulana kadar
        ChatServer.this.start();
    }

    // Gelen bağlantı isteklerini dinler
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            serverSocket = tmp;
        }

        /*
        * Eğer herhangi bir cihaza bağlı değil ise gelen isteği kabul etmek üzere Bluetooth socket oluşturulur.
        * Eğer listen yada connecting durumunda ise gelen soket ve cihaz bilgilerine göre bağlantı başlatılır.
        * Cihaz zaten bağlı ise yeni bağlantı isteği için açılan soket kapatılır.
        * Karşı taraftan gelen istekleri dinler.
        * */
        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != STATE_CONNECTED) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                if (socket != null) {
                    synchronized (ChatServer.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // Bağlantı kurmak için kullanılır.
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run() {
            setName("ConnectThread");

            // Bağlantı başlatma işlemi gerçekleşeceğinden cihaz keşfetme işlemini durdur
            bluetoothAdapter.cancelDiscovery();

            // Bluetooth Socket'e bağlan
            try {
                socket.connect();
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e2) {
                }
                connectionFailed();
                return;
            }

            // ConnectThreadi sıfırla
            synchronized (ChatServer.this) {
                connectThread = null;
            }

            // Okuma ve yazma işlemlerini başlatma için gerekli threadi çalıştır.
            //Bu işlemi başlatmak için bluetooth soket ve cihaz bilgilerini gönder(Bluetooth Mac Adress)
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    // Diğer cihaza bağlandıktan sonra çalışır. Okuma yazma durumları için gerekli threadleri oluşturur.
    // Açılan soket yardımıyla veri gönderir ve alır.
    private class ReadWriteThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(BluetoothSocket socket) {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // CONNECTED Durumda olduğu sürece InputStream sürekli dinlenir.
            // Karşı taraftan gelen mesajlar InputStream aracılığıyla okunup dizide toplanır.
            // Dizideki mesajlar handler yardımıyla Message classına gönderilir.
            // MainActivity tarafında handler durumuna göre mesajlar okunup Listviewe aktarılır.
            // Mesaj okunduktan sonra handler durumu MESSAGE_READ olarak değiştirilir.
            while (true) {
                try {
                    bytes = inputStream.read(buffer);

                    handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1,
                            buffer).sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    ChatServer.this.start();
                    break;
                }
            }
        }

        // Edittext'ten gelen mesajı OutputStream'e verir.
        //Socket aracılığıyla mesaj diğer cihaza iletilir.
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
                        buffer).sendToTarget();
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
