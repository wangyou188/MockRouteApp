// ========== Android 开发者选项模拟路线 App ==========
//
// 功能：模拟 GPS 移动路线，可以在地图上绘制路径并模拟行走
// 适用于：测试地图App、导航App、位置相关功能测试
//
// 权限要求（AndroidManifest.xml）：
// <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION"/>
// <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
// <uses-permission android:name="android.permission.INTERNET"/>

package com.example.mockroute;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MockRouteActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private LocationManager locationManager;
    private Handler handler;
    private List<LocationPoint> routePoints = new ArrayList<>();
    
    private TextView statusText;
    private TextView routeInfoText;
    private Button startRouteBtn;
    private Button stopRouteBtn;
    private Button clearRouteBtn;
    private Button addPointBtn;
    private EditText latInput;
    private EditText lonInput;
    private EditText intervalInput;
    private EditText speedInput;
    
    private boolean isSimulating = false;
    private int currentPointIndex = 0;
    private int simulationInterval = 3000;
    private float simulationSpeed = 1.0f;
    
    // 默认路线：天安门绕圈
    private double[][] defaultRoute = {
        {39.9042, 116.3974}, {39.9045, 116.3980}, {39.9050, 116.3990},
        {39.9055, 116.4000}, {39.9050, 116.4010}, {39.9045, 116.4020},
        {39.9040, 116.4010}, {39.9035, 116.4000}, {39.9030, 116.3990},
        {39.9025, 116.3980}, {39.9020, 116.3974},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_route);

        handler = new Handler(Looper.getMainLooper());
        initViews();
        initLocationManager();
        checkPermissions();
        loadDefaultRoute();
    }

    private void initViews() {
        statusText = findViewById(R.id.statusText);
        routeInfoText = findViewById(R.id.routeInfoText);
        startRouteBtn = findViewById(R.id.startRouteBtn);
        stopRouteBtn = findViewById(R.id.stopRouteBtn);
        clearRouteBtn = findViewById(R.id.clearRouteBtn);
        addPointBtn = findViewById(R.id.addPointBtn);
        latInput = findViewById(R.id.latInput);
        lonInput = findViewById(R.id.lonInput);
        intervalInput = findViewById(R.id.intervalInput);
        speedInput = findViewById(R.id.speedInput);

        startRouteBtn.setOnClickListener(v -> startRouteSimulation());
        stopRouteBtn.setOnClickListener(v -> stopRouteSimulation());
        clearRouteBtn.setOnClickListener(v -> clearRoute());
        addPointBtn.setOnClickListener(v -> addPoint());

        intervalInput.setText("3000");
        speedInput.setText("1.0");

        updateStatus("状态: 待命\n点击添加坐标可手动添加路线点");
        updateRouteInfo();
    }

    private void initLocationManager() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void checkPermissions() {
        if (!isMockLocationEnabled()) {
            Toast.makeText(this, "请开启开发者选项中的允许模拟位置", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intent);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private boolean isMockLocationEnabled() {
        try {
            int mockLocationSetting = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0);
            return mockLocationSetting != 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("权限已获取");
            } else {
                updateStatus("权限被拒绝");
            }
        }
    }

    private void loadDefaultRoute() {
        routePoints.clear();
        for (double[] point : defaultRoute) {
            routePoints.add(new LocationPoint(point[0], point[1]));
        }
        updateRouteInfo();
        Toast.makeText(this, "已加载默认路线：天安门绕圈", Toast.LENGTH_SHORT).show();
    }

    private void addPoint() {
        try {
            String latStr = latInput.getText().toString().trim();
            String lonStr = lonInput.getText().toString().trim();

            if (latStr.isEmpty() || lonStr.isEmpty()) {
                Toast.makeText(this, "请输入有效的经纬度", Toast.LENGTH_SHORT).show();
                return;
            }

            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);

            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                Toast.makeText(this, "经纬度超出范围", Toast.LENGTH_SHORT).show();
                return;
            }

            routePoints.add(new LocationPoint(lat, lon));
            updateRouteInfo();
            latInput.setText("");
            lonInput.setText("");
            Toast.makeText(this, "已添加点: " + String.format("%.6f, %.6f", lat, lon), Toast.LENGTH_SHORT).show();
            
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearRoute() {
        stopRouteSimulation();
        routePoints.clear();
        updateRouteInfo();
        updateStatus("路线已清除");
    }

    private void startRouteSimulation() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, "路线至少需要2个点", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            simulationInterval = Integer.parseInt(intervalInput.getText().toString().trim());
            simulationSpeed = Float.parseFloat(speedInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的间隔和速度", Toast.LENGTH_SHORT).show();
            return;
        }

        isSimulating = true;
        currentPointIndex = 0;
        startRouteBtn.setEnabled(false);
        stopRouteBtn.setEnabled(true);

        updateStatus("路线模拟已开始\n共 " + routePoints.size() + " 个点\n间隔: " + simulationInterval + "ms");
        simulateNextPoint();
    }

    private void simulateNextPoint() {
        if (!isSimulating || currentPointIndex >= routePoints.size()) {
            if (currentPointIndex >= routePoints.size()) {
                currentPointIndex = 0;
                updateStatus("路线已循环回到起点");
                simulateNextPoint();
            }
            return;
        }

        LocationPoint point = routePoints.get(currentPointIndex);
        boolean success = setMockLocation(point.latitude, point.longitude);

        if (success) {
            updateStatus(String.format("正在模拟: %.6f, %.6f\n进度: %d/%d", 
                    point.latitude, point.longitude, 
                    currentPointIndex + 1, routePoints.size()));
        }

        currentPointIndex++;
        handler.postDelayed(this::simulateNextPoint, simulationInterval);
    }

    private void stopRouteSimulation() {
        isSimulating = false;
        startRouteBtn.setEnabled(true);
        stopRouteBtn.setEnabled(true);
        updateStatus("路线模拟已停止");
    }

    private boolean setMockLocation(double latitude, double longitude) {
        try {
            locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, false, false, false, false, 
                    true, true, 
                    android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                    android.location.provider.ProviderProperties.ACCURACY_FINE
            );

            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

            Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
            mockLocation.setLatitude(latitude);
            mockLocation.setLongitude(longitude);
            mockLocation.setAltitude(0);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            mockLocation.setAccuracy(5.0f);
            mockLocation.setSpeed(simulationSpeed);
            mockLocation.setBearing(0);

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
            return true;

        } catch (SecurityException e) {
            runOnUiThread(() -> Toast.makeText(this, "权限不足", Toast.LENGTH_LONG).show());
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateStatus(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }

    private void updateRouteInfo() {
        StringBuilder info = new StringBuilder();
        info.append("路线点 (").append(routePoints.size()).append("):\n");
        
        int displayCount = Math.min(routePoints.size(), 5);
        for (int i = 0; i < displayCount; i++) {
            LocationPoint p = routePoints.get(i);
            info.append(String.format("  %d: %.6f, %.6f\n", i + 1, p.latitude, p.longitude));
        }
        
        if (routePoints.size() > 5) {
            info.append("  ... 还有 ").append(routePoints.size() - 5).append(" 个点\n");
        }
        
        if (routePoints.size() > 0) {
            double totalDistance = calculateTotalDistance();
            info.append(String.format("\n总距离: %.2f 米", totalDistance));
        }
        
        runOnUiThread(() -> routeInfoText.setText(info.toString()));
    }

    private double calculateTotalDistance() {
        if (routePoints.size() < 2) return 0;
        
        double totalDistance = 0;
        for (int i = 1; i < routePoints.size(); i++) {
            LocationPoint p1 = routePoints.get(i - 1);
            LocationPoint p2 = routePoints.get(i);
            totalDistance += distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
        }
        
        if (routePoints.size() > 2) {
            LocationPoint first = routePoints.get(0);
            LocationPoint last = routePoints.get(routePoints.size() - 1);
            totalDistance += distanceBetween(last.latitude, last.longitude, first.latitude, first.longitude);
        }
        
        return totalDistance;
    }

    private double distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRouteSimulation();
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}
    }

    private static class LocationPoint {
        double latitude;
        double longitude;
        LocationPoint(double lat, double lon) {
            this.latitude = lat;
            this.longitude = lon;
        }
    }
}
