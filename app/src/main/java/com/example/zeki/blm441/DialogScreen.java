package com.example.zeki.blm441;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Set;

/**
 * Created by Zeki on 3.12.2016.
 */

public class DialogScreen extends Activity{

    private ChatServer chatServer;
    private BluetoothAdapter bluetoothAdapter;
    private Activity activity;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private ArrayAdapter<String> pairedDevicesAdapter;
    private Dialog dialog;
    private Switch bluetoothStateSwitch;
    private ListView pairedDeviceList,discoveredDeviceList;

    public DialogScreen(ChatServer chatServer,BluetoothAdapter bluetoothAdapter, Activity activity){
        this.chatServer = chatServer;
        this.bluetoothAdapter = bluetoothAdapter;
        this.activity = activity;
    }

    //Dialog Ekranı oluşturulur.
    public void initialScreen(){

        dialog = new Dialog(activity);
        dialog.setContentView(R.layout.device_list);
        dialog.setTitle("Bluetooth Cihazları");

        initUIElement();
        discoveryDevices();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        activity.registerReceiver(discoveryFinishReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(discoveryFinishReceiver, filter);

        fillPairedDevicesAdapter();

        pairedDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        discoveredDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        bluetoothStateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                bluetoothTurnOnOff();
            }
        });

       dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.setCancelable(false);
        dialog.show();

    }

    //DialogScreende kullanılacak olan nesnelerin oluşturulması
    public void initUIElement (){

        //Eşleşen cihazların tutulduğu liste
        pairedDevicesAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);

        bluetoothStateSwitch = (Switch) dialog.findViewById(R.id.turnOnSwitch);
        //Listviewler oluşturuldu ve adapterler set edildi.
        pairedDeviceList = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        discoveredDeviceList = (ListView) dialog.findViewById(R.id.discoveredDeviceList);

        pairedDeviceList.setAdapter(pairedDevicesAdapter);
        discoveredDeviceList.setAdapter(discoveredDevicesAdapter);

        if(bluetoothAdapter.isEnabled())
            bluetoothStateSwitch.setChecked(true);
    }

    //Listview'de tıklanan itemin adres bilgisi chatservera gönderilir ve bağlantı sağlanır.
    public void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        chatServer.connect(device);
    }

    //Bluetooth Açık olan cihazları keşfet
    public void discoveryDevices(){

        discoveredDevicesAdapter.clear();
        //Daha önceden keşfedilebilir cihazları tarıyorsa durdur.
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        //Cihazları Keşfe Başla
        bluetoothAdapter.startDiscovery();

    }

    //Eşleşen cihazları Adaptere doldur.
    public void fillPairedDevicesAdapter(){

        pairedDevicesAdapter.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(activity.getString(R.string.eslesme_yok));
        }
    }



    //Bluetooth Özelliğini Açıp-Kapatmak için kullanılır
    public void bluetoothTurnOnOff(){
        if(bluetoothAdapter.isEnabled()){
           bluetoothAdapter.disable();
        }else{
            Intent bluetoothStart = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(bluetoothStart,1);
        }
    }

    //Keşfedilebilir Cihazları Arar. Keşif sona erdiğinde cihaz bulamazsa cihaz bulunamadı mesajı doldurur.
    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add(context.getString(R.string.cihaz_yok));
                }
                ((ProgressBar)dialog.findViewById(R.id.loadScanDevice)).setVisibility(View.GONE);
            }
        }
    };

}
