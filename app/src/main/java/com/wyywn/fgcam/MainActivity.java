package com.wyywn.fgcam;

import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener, LocationListener {

    private int facing = 1;
    private double overflowExtent = 0.2;
    private final double[] cameraAspectRatioOptions = {1,0.75,0.5625,0};
    int localAspectRatioMode = 1;

    Context context = this;

    private PreviewView previewView;
    private Preview preview;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ProcessCameraProvider cameraProvider;
    FrameLayout previewContainer;

    SeekBar seekBar;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private LocationManager locationManager;
    private File outputDirectory;
    private final String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private Double longitude,latitude;
    
    Bitmap currentBmp;
    Bitmap currentBmpOrg;

    int screenWidth;
    int screenHeight;

    float[] marginPoint = {0,0};
    float[] startPoint = {0,0};
    double scale;
    float darkExtentGlobal = 1;

    double prevDistance;

    private String img_path;
    JSONArray fgLibObj;
    JSONObject envObj;
    JSONObject settingObj;
    String exPath = Environment.getExternalStorageDirectory().getPath();
    String dataPath;
    String fgLibFileName;
    String envFileName;
    String settingFileName;

    String currentFgLibPath;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 延伸显示区域到刘海
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(lp);
        // 设置页面全屏显示
        final View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        dataPath = Objects.requireNonNull(getExternalFilesDir("")).getAbsolutePath();
        fgLibFileName = dataPath + "/fglib.json";
        envFileName = dataPath + "/env.json";
        settingFileName = dataPath + "/setting.json";

        /*DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;*/
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        defaultDisplay.getRealSize(outPoint);
        screenWidth = outPoint.x;
        screenHeight = outPoint.y;

        seekBar = findViewById(R.id.seekBar);
        ConstraintLayout.LayoutParams seekBarLayout = (ConstraintLayout.LayoutParams) seekBar.getLayoutParams();
        //seekBarLayout.setMargins(40 - seekBarLayout.width / 2,(int)(screenWidth / 0.75 / 2));

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            seekBarLayout.rightMargin = 23 - seekBarLayout.width / 2;
            seekBarLayout.bottomMargin = seekBarLayout.width / 2 - 40;
        }

        loadSetting();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivity(intent);
            }
        }

        previewView = findViewById(R.id.previewView);
        previewContainer = findViewById(R.id.previewContainer);

        if (requestPermission()) {
            initializeCamera();
        } else {
            ActivityCompat.requestPermissions(this, permissions, 1001);
        }

        getFgLib();

        // 设置输出目录
        outputDirectory = getOutputDirectory();

        // 创建一个 ExecutorService 以用于摄像头操作
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            if (settingObj.getBoolean("autoLight")){
                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }
            if (settingObj.getBoolean("gps") && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, MainActivity.this);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        bindViews();

        previewContainer.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ImageView imageView = findViewById(R.id.imageView);
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) imageView.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startPoint[0] = event.getX();
                        startPoint[1] = event.getY();

                        marginPoint[0] = layout.leftMargin;
                        marginPoint[1] = layout.topMargin;

                        break;
                    case MotionEvent.ACTION_MOVE:
                        /*seekBar = findViewById(R.id.seekBar);
                        seekBar.bringToFront();*/
                        switch (event.getPointerCount()){
                            case 1:
                                float x = event.getX();
                                float y = event.getY();

                                int left = (int) (marginPoint[0] + (x - startPoint[0]) * 1);
                                int top = (int) (marginPoint[1] + (y - startPoint[1]) * 1);

                                layout.setMargins(left, top, 0, -imageView.getHeight() * 2);
                                imageView.setLayoutParams(layout);

                                break;
                            case 2:
                                float x1 = event.getX(0);
                                float y1 = event.getY(0);
                                float x2 = event.getX(1);
                                float y2 = event.getY(1);

                                float dx = x2 - x1;
                                float dy = y2 - y1;
                                double distance = Math.sqrt(dx * dx + dy * dy);

                                if (prevDistance != 0) {
                                    double scaleFactor = distance / prevDistance;
                                    int width = imageView.getWidth();
                                    int height = imageView.getHeight();
                                    int newWidth = (int) (width * scaleFactor);
                                    int newHeight = (int) (height * scaleFactor);
                                    layout.width = newWidth;
                                    layout.height = newHeight;
                                    //Log.d("MyActivity",Double.toString(newHeight/newWidth));
                                    imageView.setLayoutParams(layout);
                                    //scale = scaleFactor;
                                }

                                prevDistance = distance;
                                break;
                        }
                        int cutBackX = layout.leftMargin + (int)(imageView.getWidth() * overflowExtent) - previewView.getLayoutParams().width;
                        int cutBackY = layout.topMargin + (int)(imageView.getHeight() * overflowExtent) - previewView.getLayoutParams().height;
                        int cutFowardX = layout.leftMargin + (int)(imageView.getWidth() * (1 - overflowExtent));
                        int cutFowardY = layout.topMargin + (int)(imageView.getHeight() * (1 - overflowExtent));
                        if (cutBackX > 0){
                            layout.leftMargin -= cutBackX;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutBackY > 0){
                            layout.topMargin -= cutBackY;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutFowardX < 0){
                            layout.leftMargin -= cutFowardX;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutFowardY < 0){
                            layout.topMargin -= cutFowardY;
                            imageView.setLayoutParams(layout);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        marginPoint[0] = layout.leftMargin;
                        marginPoint[1] = layout.topMargin;
                        prevDistance = 0;

                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.requestLayout();

                        scale = (double) imageView.getWidth() / (double) currentBmp.getWidth();

                        saveEnv();

                        break;
                }
                return true;
            }
        });
    }

    public String readFile(String empty,String fileName){
        try {
            File file = new File(fileName);
            String jsonString = empty;
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                StringBuilder text = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append("\n");
                }
                jsonString = text.toString();
            }

            return jsonString;

        } catch (IOException e) {
            // Handle file read errors
            e.printStackTrace();
        }
        return null;
    }
    public void getFgLib(){
        File file = new File(fgLibFileName);
        if (!file.exists()) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, com.wyywn.fgcam.Settings.class);
            startActivity(intent);
            Toast.makeText(context, "Please add at least a lib", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            fgLibObj = new JSONArray(readFile("[]",fgLibFileName));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        RecyclerView recyclerView = findViewById(R.id.fgLibRecycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        FgLibAdapter adapter = getFgLibAdapter();
        recyclerView.setAdapter(adapter);
    }

    @NonNull
    private FgLibAdapter getFgLibAdapter() {
        FgLibAdapter.OnItemClickListener itemClickListener = position -> {
            // 处理点击事件
            try {
                JSONObject item = fgLibObj.getJSONObject(position);
                //Toast.makeText(context, item.getString("name"), Toast.LENGTH_SHORT).show();
                currentFgLibPath = item.getString("path");
                display(item.getString("path"));

                saveEnv();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };

        return new FgLibAdapter(fgLibObj,itemClickListener);
    }

    public void loadEnv(){
        File file = new File(envFileName);
        if (!file.exists()) {
            return;
        }
        try {
            String jsonString = readFile("{}",envFileName);
            if (Objects.equals(jsonString, "{}")){
                return;
            }
            envObj = new JSONObject(jsonString);

            facing = envObj.getInt("facing");

            currentFgLibPath = envObj.getString("currentFgLibPath");
            file = new File(envFileName);
            if (file.exists()) {
                display(currentFgLibPath);
            }

            img_path = envObj.getString("currentFgPath");
            file = new File(img_path);
            if (file.exists()) {
                clickFg(img_path);
            }

            JSONArray marginPointjsonArray = new JSONArray(envObj.getString("marginPoint"));
            float[] marginPointfloatArray = new float[marginPointjsonArray.length()];
            for (int i = 0; i < marginPointjsonArray.length(); i++) {
                marginPointfloatArray[i] = (float) marginPointjsonArray.getDouble(i);
            }
            marginPoint = marginPointfloatArray;
            ImageView imageView = findViewById(R.id.imageView);
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) imageView.getLayoutParams();
            layout.leftMargin = (int)marginPoint[0];
            layout.topMargin = (int)marginPoint[1];

            if (!envObj.getString("scale").equals("0.0")){
                scale = Double.parseDouble(envObj.getString("scale"));
                layout.width  = (int) (currentBmp.getWidth() * scale);
                layout.height = (int) (currentBmp.getHeight() * scale);
                imageView.setLayoutParams(layout);
            }
        } catch (JSONException e) {
            //return;
            throw new RuntimeException(e);
        }

    }
    public void saveEnv(){
        if (currentFgLibPath == null || img_path == null){
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject();

            JSONArray marginPointjsonArray = new JSONArray();
            for (float value : marginPoint) {
                marginPointjsonArray.put(value);
            }
            jsonObject.put("marginPoint",marginPointjsonArray.toString());

            jsonObject.put("scale", Double.toString(scale));
            jsonObject.put("currentFgLibPath", currentFgLibPath);
            jsonObject.put("currentFgPath", img_path);
            jsonObject.put("facing",facing);

            String jsonString = jsonObject.toString();

            FileWriter writer = new FileWriter(envFileName);
            writer.write(jsonString);
            writer.close();
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void saveSetting(){
        try {
            FileWriter writer = new FileWriter(settingFileName);
            writer.write(settingObj.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void loadSetting(){
        settingObj = new JSONObject();
        try {
            File file = new File(settingFileName);
            if (!file.exists()){
                InputStream inputStream = context.getResources().openRawResource(R.raw.template_setting);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                //Toast.makeText(context,stringBuilder.toString(),Toast.LENGTH_LONG).show();
                JSONArray template = new JSONArray(stringBuilder.toString());

                for (int i = 0;i < template.length();i++){
                    JSONObject item = template.getJSONObject(i);
                    if (item == null || !item.has("valueType") || !item.has("name") || !item.has("defaultValue")) {
                        continue;
                    }
                    switch (item.getString("valueType")){
                        case "boolean":
                            settingObj.put(item.getString("name"),item.getBoolean("defaultValue"));
                            break;
                        case "double":
                            settingObj.put(item.getString("name"),item.getDouble("defaultValue"));
                            break;
                        case "string":
                            settingObj.put(item.getString("name"),item.getString("defaultValue"));
                            break;
                    }
                }
                saveSetting();
            }
            else {
                settingObj = new JSONObject(readFile("{}",settingFileName));

                overflowExtent = settingObj.getDouble("overflowExtent");

            }
        } catch (IOException | JSONException | NullPointerException e){
            String errorMessage = "E " + e.getMessage();
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    public static List<String> listFiles(String directoryPath) {
        List<String> fileList = new ArrayList<>();
        File directory = new File(directoryPath);

        // 检查目录是否存在
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            Arrays.sort(files);
            if (files != null) {
                for (File file : files) {
                    // 如果是文件，将文件路径添加到列表中
                    if (file.isFile()) {
                        String name = file.getName();
                        String[] arr = name.split("\\.");
                        String[] allowed = {"png","jpg","bmp"};
                        if (Arrays.asList(allowed).contains(arr[arr.length - 1])){
                            fileList.add(file.getAbsolutePath());
                        }
                    }
                }
            } else {
                System.err.println("Failed to list files. Directory may be empty or inaccessible.");
            }
        } else {
            System.err.println("Directory does not exist or is not a directory.");
        }

        return fileList;
    }

    public void adjustPreviewSize(){
        //Toast.makeText(context, Integer.toString(screenWidth), Toast.LENGTH_SHORT).show();
        /*previewView.getLayoutParams().height = screenWidth * 4 / 3;
        previewView.getLayoutParams().width = screenWidth;*/
        View previewContainer = findViewById(R.id.previewContainer);
        double cameraAspectRatio = cameraAspectRatioOptions[localAspectRatioMode];
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            int height = (int)(screenWidth / cameraAspectRatio);
            previewContainer.getLayoutParams().height = height;
            previewContainer.getLayoutParams().width = screenWidth;

            previewView.getLayoutParams().width = screenWidth;
            previewView.getLayoutParams().height = height;
        }
        else {
            previewContainer.getLayoutParams().width = (int)(screenHeight / cameraAspectRatio);
            previewContainer.getLayoutParams().height = screenHeight;

            previewView.getLayoutParams().width = (int)(screenHeight / cameraAspectRatio);
            previewView.getLayoutParams().height = screenHeight;
        }
        if (cameraAspectRatio == 0){
            previewContainer.getLayoutParams().width = screenWidth;
            previewContainer.getLayoutParams().height = screenHeight;

            previewView.getLayoutParams().width = screenWidth;
            previewView.getLayoutParams().height = screenHeight;
        }
    }
    @SuppressLint("SetTextI18n")
    public void onChangeAspectRatioButtonClick(View view) {
        /*View recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setVisibility(View.VISIBLE);*/
        localAspectRatioMode += 1;
        if (localAspectRatioMode >= cameraAspectRatioOptions.length){
            localAspectRatioMode = 0;
        }
        Button button = findViewById(R.id.changeAspectRatioButton);
        String text = "";
        //private final double[] cameraAspectRatioOptions = {1,0.75,0.5625,0};
        switch (Double.toString(cameraAspectRatioOptions[localAspectRatioMode])){
            case "1.0":
                text = "1:1";
                break;
            case "0.75":
                text = "4/3";
                break;
            case "0.5625":
                text = "16/9";
                break;
            case "0.0":
                text = "full";
                break;
        }
        button.setText(text);
        adjustPreviewSize();
    }

    public void clickFg(String path){
        img_path = path;
        saveEnv();
        currentBmpOrg = BitmapFactory.decodeFile(img_path);
        currentBmp = currentBmpOrg;

        ImageView iv= findViewById(R.id.imageView);
        iv.setImageBitmap(currentBmp);

        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) iv.getLayoutParams();
        layout.width = currentBmp.getWidth();
        layout.height = currentBmp.getHeight();
        iv.setLayoutParams(layout);

        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.requestLayout();

        adjustLight(darkExtentGlobal);

        //Toast.makeText(context, Double.toString((float)iv.getHeight() / (float)iv.getWidth()), Toast.LENGTH_SHORT).show();
    }
    public void display(String location){
        List<String> fileList = listFiles(exPath+location);
        ArrayList<String> arrayList = new ArrayList<>(fileList);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        CustomAdapter.OnItemClickListener itemClickListener = position -> {
            // 处理点击事件
            String selectedItem = fileList.get(position);
            // 进行相应操作
            clickFg(selectedItem);
        };

        CustomAdapter adapter = new CustomAdapter(arrayList,itemClickListener);
        recyclerView.setAdapter(adapter);
    }

    private void initializeCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider,facing);
                adjustPreviewSize();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean requestPermission() {
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        return allPermissionsGranted;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0;i < permissions.length;i++){
            if (requestCode == 1001) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeCamera();
                }

            }
        }
    }

    private void bindPreview(ProcessCameraProvider cameraProvider, int facingLocal) {
        preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(facingLocal)
                .build();
        //Toast.makeText(context, Integer.toString(facingLocal), Toast.LENGTH_SHORT).show();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
    }

    public void onSwitchCameraButtonClick(View view) {
        if (facing == 1){
            facing = 0;
        }
        else if(facing == 0){
            facing = 1;
        }
        cameraProviderFuture.addListener(() -> {
            cameraProvider.unbind(preview, imageCapture);
            initializeCamera();
        }, ContextCompat.getMainExecutor(this));
        saveEnv();
    }

    private File getOutputDirectory() {
        File mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File outputDir = new File(mediaDir, "fgcam");

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        return outputDir;
    }

    public void onTakePhotoButtonClick(View view) {
        String format;
        try {
            format = settingObj.getString("photoNameFormat");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        Date currentDate = new Date();
        File photoFile = new File(outputDirectory, "temp-"+dateFormat.format(currentDate)+".jpg");
        //Toast.makeText(context, outputDirectory.getPath(), Toast.LENGTH_SHORT).show();

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // 图片保存成功
                String savedUri = photoFile.getAbsolutePath();

                String[] exifKeys = {
                        ExifInterface.TAG_APERTURE,
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_IMAGE_LENGTH,
                        ExifInterface.TAG_IMAGE_WIDTH,
                        ExifInterface.TAG_ISO,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_WHITE_BALANCE
                };
                String[] exifData = getExifData(exifKeys,savedUri);

                BitmapFactory.Options options = new BitmapFactory.Options();
                //options.inSampleSize = 8; // 缩小图像以避免OOM
                Bitmap bitmap = BitmapFactory.decodeFile(savedUri, options);
                //Bitmap bitmapToAdd = BitmapFactory.decodeFile(img_path, options);

                //Bitmap bitmapOut = addImageToBitmap(bitmap, bitmapToAdd, marginPoint);
                Bitmap bitmapOut = addImageToBitmap(bitmap, currentBmp, marginPoint);
                saveBitmapToStorage(bitmapOut,exifKeys,exifData);

                try {
                    if (!settingObj.getBoolean("saveOrigin")){
                        File file = new File(savedUri);
                        file.delete();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // 图片保存失败
                runOnUiThread(() -> Toast.makeText(context, "照片保存失败: " + exception.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private Bitmap toHorizontalMirror(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(-1f, 1f); // 水平镜像翻转
        return Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);
    }
    private Bitmap addImageToBitmap(Bitmap bitmapOrg, Bitmap bitmapToAdd, float[] marginPoint) {
        if (facing == 0){
            bitmapOrg = toHorizontalMirror(bitmapOrg);
        }

        double cameraAspectRatio = cameraAspectRatioOptions[localAspectRatioMode];
        int orientation = getResources().getConfiguration().orientation;
        if (cameraAspectRatio == 0.0){
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                cameraAspectRatio = (double) screenWidth / (double) screenHeight;
            }
            else {
                cameraAspectRatio = (double) screenHeight / (double) screenWidth;
            }
        }
        //int orientation = getResources().getConfiguration().orientation;
        Bitmap bitmap;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (bitmapOrg.getWidth() >= bitmapOrg.getHeight() * cameraAspectRatio) {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        (int) (bitmapOrg.getWidth() / 2 - (bitmapOrg.getHeight() * cameraAspectRatio) / 2),
                        0,
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio),
                        bitmapOrg.getHeight()
                );
            } else {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        0,
                        bitmapOrg.getWidth(),
                        (int) (bitmapOrg.getWidth() * cameraAspectRatio)
                );
            }
        }
        else {
            if (bitmapOrg.getHeight() >= bitmapOrg.getWidth() * cameraAspectRatio) {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        (int) (bitmapOrg.getHeight() / 2 - (bitmapOrg.getWidth() * cameraAspectRatio) / 2),
                        bitmapOrg.getHeight(),
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio)
                );
            } else {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        0,
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio),
                        bitmapOrg.getHeight()
                );
            }
        }

        // 创建一个新的 Bitmap，尺寸和原始图像相同
        Bitmap newBitmap = bitmap.copy(bitmap.getConfig(), true);

        // 创建画布并将其连接到新的 Bitmap 上
        Canvas canvas = new Canvas(newBitmap);

        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, Float.toString(marginPoint[1]), Toast.LENGTH_SHORT).show();
            }
        });*/

        ImageView imageView = findViewById(R.id.imageView);
        View previewContainer = findViewById(R.id.previewContainer);

        float picScale = (float)bitmap.getWidth() / (float)previewContainer.getLayoutParams().width;
        //float potScale = (float)imageView.getWidth() / (float)previewContainer.getLayoutParams().width;

        bitmapToAdd = Bitmap.createScaledBitmap(bitmapToAdd
                , Math.round((float)imageView.getWidth() / (float)previewContainer.getLayoutParams().width * bitmap.getWidth())
                , Math.round((float)imageView.getHeight() / (float)previewContainer.getLayoutParams().height * bitmap.getHeight())
                , true);

        canvas.drawBitmap(bitmapToAdd, marginPoint[0] * picScale, marginPoint[1] * picScale, null);

        try {
            if (settingObj.getBoolean("timeWatermark")){
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setTextSize(50);
                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(settingObj.getString("timeWatermarkFormat"));
                Date currentDate = new Date();
                canvas.drawText(dateFormat.format(currentDate),5,bitmap.getHeight() - 40,paint);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return newBitmap;
    }

    // 保存 Bitmap 到设备存储的方法
    private void saveBitmapToStorage(Bitmap bitmap, String[] exifKeys, String[] exifData) {
        try {
            // 创建一个输出文件来保存图像
            File outputDirectory = getOutputDirectory();
            String format = settingObj.getString("photoNameFormat");
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            Date currentDate = new Date();
            String photoName = dateFormat.format(currentDate)+".jpg";

            final File outputFile = new File(outputDirectory, photoName);
            // 创建输出流
            FileOutputStream fos = new FileOutputStream(outputFile);
            // 将 Bitmap 压缩为 JPEG 格式并写入文件
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            // 关闭流
            fos.close();

            String imagePath = outputDirectory + "/" + photoName;

            String toastTexts = "照片";
            if (settingObj.getBoolean("exif")){
                saveExifData(imagePath,exifKeys,exifData);
                toastTexts += "+exif";
            }
            if (settingObj.getBoolean("gps") && longitude != null){
                addGPSToImage(imagePath, latitude, longitude);
                toastTexts += "+gps";
            }
            toastTexts += "已保存";

            String finalToastTexts = toastTexts;
            runOnUiThread(() -> Toast.makeText(context, finalToastTexts, Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void addGPSToImage(String imagePath, double latitude, double longitude) {
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);

            double exifLatitude = Math.abs(latitude);
            String exifLatitudeRef = latitude >= 0 ? "N" : "S";

            double exifLongitude = Math.abs(longitude);
            String exifLongitudeRef = longitude >= 0 ? "E" : "W";

            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDms(exifLatitude));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exifLatitudeRef);
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDms(exifLongitude));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exifLongitudeRef);
            exifInterface.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static String convertToDms(double coord) {
        int degrees = (int) coord;
        coord = (coord - degrees) * 60;
        int minutes = (int) coord;
        coord = (coord - minutes) * 60;
        int seconds = (int) (coord * 1000);

        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }
    private String[] getExifData(String[] keys, String path){
        ExifInterface exifInterface;
        try {
            exifInterface = new ExifInterface(path);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] values = new String[keys.length];

        for (int i = 0;i < keys.length;i++) {
            String value = exifInterface.getAttribute(keys[i]);
            values[i] = value;
        }
        return values;
    }
    private void saveExifData(String imagePath, String[] keys, String[] data){
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            for (int i = 0;i < keys.length;i++) {
                exifInterface.setAttribute(keys[i], data[i]);
            }
            exifInterface.saveAttributes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void onSettingButtonClick(View view) {
        saveEnv();
        if (lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, com.wyywn.fgcam.Settings.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getFgLib();
        loadEnv();
        loadSetting();

        try {
            if (lightSensor != null && settingObj.getBoolean("autoLight")) {
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (!settingObj.getBoolean("autoLight") && lightSensor != null){
                sensorManager.unregisterListener(this);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 传感器精度变化时的处理逻辑
    }

    public void adjustLight(float darkExtent){
        if (currentBmpOrg == null){
            return;
        }
        ImageView imageView = findViewById(R.id.imageView);
        Bitmap newBitmap = Bitmap.createBitmap(currentBmpOrg.getWidth(),currentBmpOrg.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();

        ColorMatrix light = new ColorMatrix(new float[]{
                darkExtent, 0, 0, 0, 0,
                0, darkExtent, 0, 0, 0,
                0, 0, darkExtent, 0, 0,
                0, 0, 0, 1, 0,
        });

        paint.setColorFilter(new ColorMatrixColorFilter(light));
        canvas.drawBitmap(currentBmpOrg, 0, 0, paint);

        currentBmp = newBitmap;
        imageView.setImageBitmap(currentBmp);
        darkExtentGlobal = darkExtent;
    }

    private void bindViews() {
        seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustLight((float) progress / 100);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

                //Toast.makeText(context, "触碰SeekBar", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(context, "放开SeekBar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public float lx2de(float lx){
        float de;
        float lowest = 0.2f;
        float minLx = 2;
        float maxLx = 102;
        if (lx <= minLx){
            de = lowest;
        } else if (lx > minLx && lx <= maxLx) {
            de = (lx - minLx) * (1 - lowest) / (maxLx - minLx) + lowest;
        }
        else {
            de = 1;
        }
        return de;
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lightIntensity = event.values[0];
            /*TextView lightIntensityTextView = findViewById(R.id.lightValue);
            lightIntensityTextView.setText("光线强度：" + lightIntensity + " lx");*/
            adjustLight(lx2de(lightIntensity));

            seekBar = findViewById(R.id.seekBar);
            seekBar.setProgress((int)((double)lx2de(lightIntensity) * 100));
        }
    }

    // 当位置改变时执行，除了移动设置距离为 0时
    @Override
    public void onLocationChanged(@NonNull Location location) {
        // 获取当前纬度
        latitude = location.getLatitude();
        // 获取当前经度
        longitude = location.getLongitude();
        //Toast.makeText(context, Double.toString(latitude), Toast.LENGTH_SHORT).show();

        // 移除位置管理器
        // 需要一直获取位置信息可以去掉这个
        locationManager.removeUpdates(this);
    }

    // 当前定位提供者状态
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.e("onStatusChanged", provider);
    }

    // 任意定位提高者启动执行
    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.e("onProviderEnabled", provider);
    }

    // 任意定位提高者关闭执行
    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.e("onProviderDisabled", provider);
    }


}
