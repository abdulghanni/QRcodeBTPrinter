package com.example.printbt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    BluetoothDevice bluetoothDevice;

    OutputStream outputStream;
    InputStream inputStream;
    Thread thread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    Button btn_generate;
    EditText et_email;
    TextView lblPrinterName;
    EditText textBox;

    private ImageView imageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Create object of controls
        Button btnConnect = (Button) findViewById(R.id.btnConnect);

        imageView = findViewById(R.id.imageView3);
        Button btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        Button btnPrint = (Button) findViewById(R.id.btnPrint);

        textBox = (EditText) findViewById(R.id.txtText);

        lblPrinterName = (TextView) findViewById(R.id.lblPrinterName);

        btn_generate=findViewById(R.id.btn_generate);
        et_email=findViewById(R.id.et_email);
        imageView=findViewById(R.id.imageView3);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    FindBluetoothDevice();
                    openBluetoothPrinter();

                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    disconnectBT();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });
        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    printData();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });

        btn_generate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email=et_email.getText().toString();
                WindowManager windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
                Display display=windowManager.getDefaultDisplay();
                Point point=new Point();
                display.getSize(point);
                int x= point.x;
                int y=point.y;

                int icon=x < y ? x:y;
                icon=icon *1/5;

                QRCodeEncoder qrCodeEncoder=new QRCodeEncoder(email,
                        null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), icon);

                try {
                    Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
                    imageView.setImageBitmap(bitmap);

                    BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
                    Bitmap bitmaps = drawable.getBitmap();
                }catch (WriterException e){
                    e.printStackTrace();
                }

            }
        });

    }



    void FindBluetoothDevice(){

        try{

            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(bluetoothAdapter==null){
                lblPrinterName.setText("No Bluetooth Adapter found");
            }
            if(bluetoothAdapter.isEnabled()){
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBT,0);
            }

            Set<BluetoothDevice> pairedDevice = bluetoothAdapter.getBondedDevices();

            if(pairedDevice.size()>0){
                for(BluetoothDevice pairedDev:pairedDevice){

                    // My Bluetoth printer name is BTP_F09F1A
                    if(pairedDev.getName().equals("CMP_300")){
                        bluetoothDevice=pairedDev;
                        lblPrinterName.setText("Bluetooth Printer Attached: "+pairedDev.getName());
                        break;
                    }
                }
            }

//            lblPrinterName.setText("Bluetooth Printer Attached");
        }catch(Exception ex){
            ex.printStackTrace();
        }

    }

    // Open Bluetooth Printer

    void openBluetoothPrinter() throws IOException {
        try{

            //Standard uuid from string //
            UUID uuidSting = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            bluetoothSocket=bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting);
            bluetoothSocket.connect();
            outputStream=bluetoothSocket.getOutputStream();
            inputStream=bluetoothSocket.getInputStream();

            beginListenData();

        }catch (Exception ex){

        }
    }

    void beginListenData(){
        try{

            final Handler handler =new Handler();
            final byte delimiter=10;
            stopWorker =false;
            readBufferPosition=0;
            readBuffer = new byte[1024];

            thread=new Thread(new Runnable() {
                @Override
                public void run() {

                    while (!Thread.currentThread().isInterrupted() && !stopWorker){
                        try{
                            int byteAvailable = inputStream.available();
                            if(byteAvailable>0){
                                byte[] packetByte = new byte[byteAvailable];
                                inputStream.read(packetByte);

                                for(int i=0; i<byteAvailable; i++){
                                    byte b = packetByte[i];
                                    if(b==delimiter){
                                        byte[] encodedByte = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer,0,
                                                encodedByte,0,
                                                encodedByte.length
                                        );
                                        final String data = new String(encodedByte,"US-ASCII");
                                        readBufferPosition=0;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                lblPrinterName.setText(data);
                                            }
                                        });
                                    }else{
                                        readBuffer[readBufferPosition++]=b;
                                    }
                                }
                            }
                        }catch(Exception ex){
                            stopWorker=true;
                        }
                    }

                }
            });

            thread.start();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    // Printing Text to Bluetooth Printer //

    void printData() throws  IOException{
//        // Get the print manager.
//        PrintHelper printHelper = new PrintHelper(this);
//        // Set the desired scale mode.
//        printHelper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
//        // Get the bitmap for the ImageView's drawable.
//        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
//        // Print the bitmap.
//        printHelper.printBitmap("Print Bitmap", bitmap);


//        try {
//            Bitmap bmp = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.qrcode);
//            if(bmp!=null){
//                byte[] command = Utils.decodeBitmap(bmp);
//                printText(command);
//            }else{
//                Log.e("Print Photo error", "the file isn't exists");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e("PrintTools", "the file isn't exists");
//        }

//        Bitmap bitmap=BitmapFactory.decodeResource(getResources(), R.drawable.qrcode);
//        ByteArrayOutputStream stream=new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
//        byte[] image=stream.toByteArray();


//        try{
//            Bitmap bmp = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.qrcode);
////            msg+="\n";
//            outputStream.write(bmp.getB());
//            lblPrinterName.setText("Printing Text...");
//        }catch (Exception ex){
//            ex.printStackTrace();
//        }


//        BitmapDrawable drawable = (BitmapDrawable) iv_qrcode.getDrawable();
//        Bitmap bitmap = drawable.getBitmap();
        printPhoto();

//        try{
//            String msg = textBox.getText().toString();
//            msg+="\n";
//            outputStream.write(msg.getBytes());
//            lblPrinterName.setText("Printing Text...");
//        }catch (Exception ex){
//            ex.printStackTrace();
//        }
    }

    public void printPhoto() {

        imageView = findViewById(R.id.imageView3);

        try {
//            Bitmap bmp = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.supfa);



            Bitmap bmp = ((BitmapDrawable)imageView.getDrawable()).getBitmap();



            if(bmp!=null){
                byte[] command = Utils.decodeBitmap(bmp);
                outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                printText(command);
            }else{
                Log.e("Print Photo error", "the file isn't exists");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrintTools", "the file isn't exists");
        }
    }

    private void printText(byte[] msg) {
        try {
            // Print normal text
            outputStream.write(msg);
            printNewLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printNewLine() {
        try {
            outputStream.write(PrinterCommands.FEED_LINE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Disconnect Printer //
    void disconnectBT() throws IOException{
        try {
            stopWorker=true;
            outputStream.close();
            inputStream.close();
            bluetoothSocket.close();
            lblPrinterName.setText("Printer Disconnected.");
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

}
