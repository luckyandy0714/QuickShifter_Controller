package com.example.quickshifter_controller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.tabs.TabLayoutMediator;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FINE_LOCATION_PERMISSION = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setup();
        wait_page(() -> {
            register_activity_Main_event();
            register_page_1_event();
            register_page_2_event();
            register_page_3_event();
            Set_BusyBar(false);
        });
    }

    page_1 page_1 = new page_1();
    page_2 page_2 = new page_2();
    page_3 page_3 = new page_3();

    void setup() {
        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//關閉螢幕維持
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//維持開啟

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_PERMISSION);

        MainPagerAdapter myAdapter = new MainPagerAdapter(getSupportFragmentManager(), getLifecycle());
        myAdapter.addFragment(page_2);
        myAdapter.addFragment(page_1);
        myAdapter.addFragment(page_3);

        ViewPager2 myViewPager = findViewById(R.id.My_viewpager);
        myViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        myViewPager.setOffscreenPageLimit(3);
        myViewPager.setAdapter(myAdapter);
        myViewPager.setCurrentItem(1,false);
        new TabLayoutMediator(findViewById(R.id.tab_layout), myViewPager, (tab, position) -> {
            tab.setText((new String[]{"Setup", "Monitor", "Time Gain"})[position]);
        }).attach();
    }

    void Set_BusyBar(boolean show) {
        this.runOnUiThread(() -> findViewById(R.id.BusyBar).setVisibility(show ? View.VISIBLE : View.INVISIBLE));
    }

    View page_1_view, page_2_view, page_3_view;

    interface find_view {
        void Callback();
    }

    void wait_page(find_view callback) {
        new Thread(() -> {
            while ((page_1_view = page_1.view) == null || (page_2_view = page_2.view) == null || (page_3_view = page_3.view) == null) {
                try {
                    Thread.sleep(100);
                } catch (Exception ignored) {
                }
            }
            this.runOnUiThread(callback::Callback);
        }
        ).start();
    }

    View button_bluetooth_menu, button_reset_default_value, button_updata, button_setting_page;
    BluetoothConnect bluetooth;
    String bluetooth_MAC = null;
    boolean updating = false;

    void register_activity_Main_event() {
        bluetooth = new BluetoothConnect(this);
        SQL_DataBase database = new SQL_DataBase(getApplicationContext());

        button_bluetooth_menu = this.findViewById(R.id.button_bluetooth_menu);
        button_reset_default_value = this.findViewById(R.id.button_reset_default_value);
        button_updata = this.findViewById(R.id.button_updata);
        button_setting_page = this.findViewById(R.id.button_setting_page);

        if ((bluetooth_MAC = database.Select_data("BluetoothAddress")) == null) {
            bluetooth.get_bluetooth_Mac_menu(false);
        } else {
            bluetooth.set_Bluetooth_Address(bluetooth_MAC);
            bluetooth.Start_Connect();
        }
        button_bluetooth_menu.setOnClickListener((view) -> {
            bluetooth.get_bluetooth_Mac_menu(true);
        });
        bluetooth.SelectMacCallBack = (String Mac) -> {
            bluetooth_MAC = Mac;
            this.runOnUiThread(() -> database.Update_data("BluetoothAddress", bluetooth_MAC));
        };
        bluetooth.ConnectCallBack = (String Mac) -> {
            bluetooth.WriteLine("r1");
            bluetooth.WriteLine("l1");
            bluetooth.WriteLine("m1");
        };

        button_reset_default_value.setOnClickListener((view) -> {
            if (updating) {
                Toast.makeText(this, "忙碌中.....", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "重置中.....", Toast.LENGTH_SHORT).show();
            updating = true;
            new Thread(() -> {
                try {
                    Set_BusyBar(true);
                    bluetooth.WriteLine("m0");
                    bluetooth.WriteLine("w99");
                    Thread.sleep(1500);
                    Set_BusyBar(false);
                    updating = false;
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }).start();
        });
        button_updata.setOnClickListener((view) -> {
            if (updating) {
                Toast.makeText(this, "忙碌中.....", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "上傳中.....", Toast.LENGTH_SHORT).show();
            updating = true;
            Set_BusyBar(true);
            new Thread(() -> {
                bluetooth.WriteLine("m0");

                int[] write_buffer = new int[SETDATA_SIZE];
                Get_SetData_Array(write_buffer);

                boolean error_result = false;
                for (int i = 0; i < 33; i++) {
                    if (write_buffer[i] > 255) {
                        error_result = true;
                        break;
                    }
                }
                if (error_result) {
                    this.runOnUiThread(() -> Toast.makeText(this, "資料錯誤，上傳失敗", Toast.LENGTH_SHORT).show());
                    bluetooth.WriteLine("r1");
                    Set_BusyBar(false);
                    return;
                }
                try {
                    Thread.sleep(100);
                    bluetooth.WriteLine("w0");
                    StringBuilder write_buffer_String = new StringBuilder();
                    for (int i = 0; i < SETDATA_SIZE; i++)
                        write_buffer_String.append(write_buffer[i]).append(",");
                    write_buffer_String.append("!");
                    bluetooth.WriteLine(write_buffer_String.toString());
                    Thread.sleep(100);
                    bluetooth.WriteLine("w1");
                    Thread.sleep(100);
                    bluetooth.WriteLine("k0");
                    Thread.sleep(1500);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.runOnUiThread(() -> Toast.makeText(this, "上傳完畢", Toast.LENGTH_SHORT).show());
                Set_BusyBar(false);
                updating = false;
            }).start();
        });
        button_setting_page.setOnClickListener((view) -> {
            bluetooth.WriteLine("k0");
        });
    }

    final int SETDATA_SIZE = 6 + 2 + 2 + 10 + 10 + 5;
    int cut_enable, gear_scale_invert, gear_scale_sensitivity, engine_start_delay, cut_time, cut_smooth_time;
    int[] stable_delay = new int[2];
    int[] cut_valve = new int[2];
    int[] S_gain_map = new int[10];
    int[] T_gain_map = new int[10];
    int[] G_gain_map = new int[5];

    int[] S_gain_map_lab_ = new int[10];
    int[] T_gain_map_lab_ = new int[10];
    int[] G_gain_map_lab_ = new int[]{1, 2, 3, 4, 5};

    int cut_test_time = 80;

    ProgressBar S_progressBar;

    TextView textview_S, textview_T, textview_G, textview_Sensor;
    EditText edittext_cut_test_time;
    TextView textview_cut_detail_1, textview_cut_detail_2;

    View button_cut_enable, button_cut_test, button_clear_log, button_send_command;
    TextView log;
    EditText write_command;

    void Get_SetData_Array(int[] data_array) {
        int data_add = 0;
        data_array[data_add++] = cut_enable;
        data_array[data_add++] = gear_scale_invert;
        data_array[data_add++] = gear_scale_sensitivity;
        data_array[data_add++] = engine_start_delay;
        data_array[data_add++] = cut_time;
        data_array[data_add++] = cut_smooth_time;

        for (int i = 0; i < stable_delay.length; i++)
            data_array[data_add++] = stable_delay[i];
        for (int i = 0; i < cut_valve.length; i++)
            data_array[data_add++] = cut_valve[i];
        for (int i = 0; i < S_gain_map.length; i++)
            data_array[data_add++] = S_gain_map[i];
        for (int i = 0; i < T_gain_map.length; i++)
            data_array[data_add++] = T_gain_map[i];
        for (int i = 0; i < G_gain_map.length; i++)
            data_array[data_add++] = G_gain_map[i];
    }

    void Set_SetData_Array(int[] data_array) {
        int data_add = 0;
        cut_enable = data_array[data_add++];
        gear_scale_invert = data_array[data_add++];
        gear_scale_sensitivity = data_array[data_add++];
        engine_start_delay = data_array[data_add++];
        cut_time = data_array[data_add++];
        cut_smooth_time = data_array[data_add++];

        for (int i = 0; i < stable_delay.length; i++)
            stable_delay[i] = data_array[data_add++];
        for (int i = 0; i < cut_valve.length; i++)
            cut_valve[i] = data_array[data_add++];
        for (int i = 0; i < S_gain_map.length; i++)
            S_gain_map[i] = data_array[data_add++];
        for (int i = 0; i < T_gain_map.length; i++)
            T_gain_map[i] = data_array[data_add++];
        for (int i = 0; i < G_gain_map.length; i++)
            G_gain_map[i] = data_array[data_add++];
    }


    void register_page_1_event() {
        textview_S = page_1_view.findViewById(R.id.textview_S);
        textview_T = page_1_view.findViewById(R.id.textview_T);
        textview_G = page_1_view.findViewById(R.id.textview_G);
        textview_Sensor = page_1_view.findViewById(R.id.textview_Sensor);
        S_progressBar = page_1_view.findViewById(R.id.S_progressBar);

        edittext_cut_test_time = page_1_view.findViewById(R.id.edittext_cut_test_time);

        button_cut_enable = page_1_view.findViewById(R.id.button_cut_enable);
        button_cut_test = page_1_view.findViewById(R.id.button_cut_test);
        button_clear_log = page_1_view.findViewById(R.id.button_clear_log);
        button_send_command = page_1_view.findViewById(R.id.button_send_command);

        textview_cut_detail_1 = page_1_view.findViewById(R.id.textview_cut_detail_1);
        textview_cut_detail_2 = page_1_view.findViewById(R.id.textview_cut_detail_2);

        log = page_1_view.findViewById(R.id.log);
        write_command = page_1_view.findViewById(R.id.write_command);

        log.setMovementMethod(ScrollingMovementMethod.getInstance());

        button_clear_log.setOnClickListener((view) -> {
            log.setText("");
        });
        button_send_command.setOnClickListener((view) -> {
            write_command.clearFocus();
            bluetooth.WriteLine(write_command.getText().toString().trim());
        });
        write_command.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        write_command.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            Toast.makeText(MainActivity.this, "command : " + ((EditText) view).getText().toString(), Toast.LENGTH_SHORT).show();
        });

        bluetooth.WriteCallBack = (String write_data) -> {
            if (write_data.length() <= 0)
                return;
            runOnUiThread(() -> {
                String last_string = log.getText().toString();
                if (last_string.length() > 0) {
                    if (last_string.charAt(last_string.length() - 1) == '\n')
                        log.append("[" + new SimpleDateFormat("hh:mm:ss").format(new Date()) + "]<- ");
                }
                log.append(write_data);
                final Layout layout = log.getLayout();
                if (layout != null) {
                    int scrollDelta = layout.getLineBottom(log.getLineCount() - 1) - log.getScrollY() - log.getHeight();
                    if (scrollDelta > 0)
                        log.scrollBy(0, scrollDelta);
                }
            });
        };

        bluetooth.ReadCallBack = (String read_data) -> {
            if (read_data.length() <= 0)
                return;
            runOnUiThread(() -> {
                if (read_data.charAt(0) != 'm') {
                    log.append("[" + new SimpleDateFormat("hh:mm:ss").format(new Date()) + "]-> " + read_data);
                    final Layout layout = log.getLayout();
                    if (layout != null) {
                        int scrollDelta = layout.getLineBottom(log.getLineCount() - 1) - log.getScrollY() - log.getHeight();
                        if (scrollDelta > 0)
                            log.scrollBy(0, scrollDelta);
                    }
                }

                String[] data = read_data.substring(1).replace("\n", "").replace("\r", "").replace("!", "").split(",");
                switch (read_data.substring(0, 1)) {
                    case "m":
                        if (data.length != 6)
                            return;
                        for (int i = 0; i < 4; i++) {
                            ((TextView) (new Object[]{textview_S, textview_T, textview_G, textview_Sensor})[i]).setText(data[i]);
                        }
                        int S = Integer.parseInt(data[0]);
                        int S_progressBar_value = (int) (((double) S / (double) Math.max(S_gain_map_lab_[9], 1)) * 100.0);

                        S_progressBar.setProgress(S_progressBar_value);
                        if (S < S_gain_map_lab_[0])
                            S_progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00FF99")));
                        else if (S >= S_gain_map_lab_[0] && S < S_gain_map_lab_[2])
                            S_progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00FF00")));
                        else if (S >= S_gain_map_lab_[2] && S < S_gain_map_lab_[5])
                            S_progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFFF00")));
                        else if (S >= S_gain_map_lab_[5] && S < S_gain_map_lab_[8])
                            S_progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF8800")));
                        else
                            S_progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF0000")));

                        textview_T.append("%");
                        textview_Sensor.append("%");
                        if (Objects.equals(data[2], "0"))
                            textview_G.setText("N");
                        break;
                    case "r":
                        if (data.length != SETDATA_SIZE)
                            return;
                        int[] int_data = new int[SETDATA_SIZE];
                        boolean error_break = false;
                        for (int i = 0; i < SETDATA_SIZE; i++) {
                            try {
                                int_data[i] = Integer.parseInt(data[i]);
                            } catch (Exception ex) {
                                System.out.println(ex);
                            }
                            if (int_data[i] > 255)
                                error_break = true;
                        }
                        if (error_break) {
                            this.runOnUiThread(() -> Toast.makeText(this, "資料錯誤，重新讀取", Toast.LENGTH_SHORT).show());
                            bluetooth.WriteLine("r1");
                            return;
                        }
                        Set_SetData_Array(int_data);
                        this.runOnUiThread(() -> {
                            try {
                                ((ToggleButton) button_cut_enable).setChecked(cut_enable == 1);
                                edittext_cut_time.setText(cut_time + "");
                                edittext_cut_smooth_time.setText(cut_smooth_time + "");
                                edittext_stable_delay_1.setText(stable_delay[0] + "");
                                edittext_stable_delay_2.setText(stable_delay[1] + "");
                                edittext_engine_start_delay.setText((engine_start_delay * 10) + "");
                                ((ToggleButton) button_gear_scale_invert).setChecked(gear_scale_invert == 1);
                                edittext_gear_scale_sensitivity.setText(gear_scale_sensitivity + "");
                                edittext_cut_valve.setText(cut_valve[0] + "");
                            } catch (Exception e) {
                                System.out.println(e);
                            }
                            refresh_time_map();
                        });
                        break;
                    case "l":
                        if (data.length != 20)
                            return;
                        int[] lab_data = new int[20];
                        boolean lab_error_break = false;
                        for (int i = 0; i < 20; i++) {
                            try {
                                lab_data[i] = Integer.parseInt(data[i]);
                            } catch (Exception ex) {
                                System.out.println(ex);
                                lab_error_break = true;
                            }
                            if (lab_data[i] > 16000)
                                lab_error_break = true;
                        }
                        if (lab_error_break) {
                            this.runOnUiThread(() -> Toast.makeText(this, "標籤資料錯誤，重新讀取", Toast.LENGTH_SHORT).show());
                            bluetooth.WriteLine("l1");
                            return;
                        }
                        for (int i = 0; i < 10; i++)
                            S_gain_map_lab_[i] = lab_data[i];
                        for (int i = 0; i < 10; i++)
                            T_gain_map_lab_[i] = lab_data[i + 10];

                        this.runOnUiThread(this::refresh_time_map);
                        break;
                    case "O":
                        if (!read_data.substring(0, 2).equals("OK"))
                            break;
                        bluetooth.WriteLine("r1");
                        bluetooth.WriteLine("l1");
                        bluetooth.WriteLine("m1");
                        break;
                    case "c":
                        if (data.length != 8)
                            return;
                        int S_Gain = 100, T_Gain = 100, G_Gain = 100, A_Gain = 100;
                        try {
                            S_Gain = (int) (Double.parseDouble(data[5]) * 100);
                            T_Gain = (int) (Double.parseDouble(data[6]) * 100);
                            G_Gain = (int) (Double.parseDouble(data[7]) * 100);
                            A_Gain = (S_Gain * T_Gain * G_Gain) / 10000;
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                        textview_cut_detail_1.setText("S:" + data[0] + "\nT:" + data[1] + "%\nG:" + data[2] + "\nC:" + data[4] + "ms");
                        textview_cut_detail_2.setText("S_Gain:" + S_Gain + "%\nT_Gain:" + T_Gain + "%\nG_Gain:" + G_Gain + "%\nA_Gain:" + A_Gain + "%");
                        break;
                }
            });
        };

        ((ToggleButton) button_cut_enable).setOnCheckedChangeListener((view, isChecked) -> {
            cut_enable = isChecked ? 1 : 0;
            bluetooth.WriteLine(cut_enable == 1 ? "e1" : "e0");
            Toast.makeText(MainActivity.this, "快排" + (isChecked ? "啟用" : "停用"), Toast.LENGTH_SHORT).show();
        });
        edittext_cut_test_time.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_cut_test_time.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            cut_test_time = Math.max(Math.min(data, 1000), 10);
            ((EditText) view).setText(cut_test_time + "");
            Toast.makeText(MainActivity.this, "cut_test_time set : " + cut_test_time, Toast.LENGTH_SHORT).show();
        });
        button_cut_test.setOnClickListener((view) -> {
            bluetooth.WriteLine("c" + cut_test_time);
        });

    }

    LinearLayout S_gain_map_lab, T_gain_map_lab, G_gain_map_lab, S_gain_map_value, T_gain_map_value, G_gain_map_value, S_gain_map_seekbar, T_gain_map_seekbar, G_gain_map_seekbar;

    void refresh_time_map() {

        S_gain_map_lab.removeAllViews();
        T_gain_map_lab.removeAllViews();
        G_gain_map_lab.removeAllViews();

        S_gain_map_value.removeAllViews();
        T_gain_map_value.removeAllViews();
        G_gain_map_value.removeAllViews();

        S_gain_map_seekbar.removeAllViews();
        T_gain_map_seekbar.removeAllViews();
        G_gain_map_seekbar.removeAllViews();

        for (int i = 0; i < 10; i++) {
            TextView textView = new TextView(this);
            textView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            textView.setGravity(Gravity.CENTER);
            textView.setText(S_gain_map_lab_[i] + "");
            textView.setTextSize(12);

            S_gain_map_lab.addView(textView);

            textView = new TextView(this);
            textView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            textView.setGravity(Gravity.CENTER);
            textView.setText(T_gain_map_lab_[i] + "%");

            T_gain_map_lab.addView(textView);
        }
        for (int i = 0; i < 5; i++) {
            TextView textView = new TextView(this);
            textView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            textView.setGravity(Gravity.CENTER);
            textView.setText(G_gain_map_lab_[i] + "");

            G_gain_map_lab.addView(textView);
        }

        for (int i = 0; i < 10; i++) {
            int finalI = i;

            EditText editText = new EditText(this);
            editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            editText.setGravity(Gravity.CENTER);
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setSelectAllOnFocus(true);
            editText.setText(S_gain_map[i] + "");
            editText.setOnEditorActionListener((view, actionId, event) -> {
                view.clearFocus();
                return false;
            });
            editText.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus)
                    return;
                int data = Integer.parseInt(((EditText) view).getText().toString().trim());
                S_gain_map[finalI] = Math.max(Math.min(data, 150), 50);
                ((EditText) view).setText(S_gain_map[finalI] + "");
                refresh_time_map();
            });
            S_gain_map_value.addView(editText);

            editText = new EditText(this);
            editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            editText.setGravity(Gravity.CENTER);
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setSelectAllOnFocus(true);
            editText.setText(T_gain_map[i] + "");
            editText.setOnEditorActionListener((view, actionId, event) -> {
                view.clearFocus();
                return false;
            });
            editText.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus)
                    return;
                int data = Integer.parseInt(((EditText) view).getText().toString().trim());
                T_gain_map[finalI] = Math.max(Math.min(data, 150), 50);
                ((EditText) view).setText(T_gain_map[finalI] + "");
                refresh_time_map();
            });
            T_gain_map_value.addView(editText);
        }
        for (int i = 0; i < 5; i++) {
            int finalI = i;

            EditText editText = new EditText(this);
            editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            editText.setGravity(Gravity.CENTER);
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setSelectAllOnFocus(true);
            editText.setText(G_gain_map[i] + "");
            editText.setOnEditorActionListener((view, actionId, event) -> {
                view.clearFocus();
                return false;
            });
            editText.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus)
                    return;
                int data = Integer.parseInt(((EditText) view).getText().toString().trim());
                G_gain_map[finalI] = Math.max(Math.min(data, 150), 50);
                ((EditText) view).setText(G_gain_map[finalI] + "");
                refresh_time_map();
            });
            G_gain_map_value.addView(editText);
        }

        int[] Max_Min_value = new int[]{200, 0, 200, 0, 200, 0};
        for (int i = 0; i < 10; i++) {
            Max_Min_value[0] = Math.min(Max_Min_value[0], S_gain_map[i]);
            Max_Min_value[1] = Math.max(Max_Min_value[1], S_gain_map[i]);
            Max_Min_value[2] = Math.min(Max_Min_value[2], T_gain_map[i]);
            Max_Min_value[3] = Math.max(Max_Min_value[3], T_gain_map[i]);
        }
        for (int i = 0; i < 5; i++) {
            Max_Min_value[4] = Math.min(Max_Min_value[4], G_gain_map[i]);
            Max_Min_value[5] = Math.max(Max_Min_value[5], G_gain_map[i]);
        }
        Max_Min_value[0] = Math.max(Max_Min_value[0] - 10, 50);
        Max_Min_value[1] = Math.min(Max_Min_value[1] + 10, 150);
        Max_Min_value[2] = Math.max(Max_Min_value[2] - 10, 50);
        Max_Min_value[3] = Math.min(Max_Min_value[3] + 10, 150);
        Max_Min_value[4] = Math.max(Max_Min_value[4] - 10, 50);
        Max_Min_value[5] = Math.min(Max_Min_value[5] + 10, 150);

        for (int i = 0; i < 10; i++) {
            int finalI = i;

            VerticalSeekBar seekBar = new VerticalSeekBar(this);
            seekBar.setMin(Max_Min_value[0]);
            seekBar.setMax(Max_Min_value[1]);
            seekBar.setProgress(S_gain_map[i]);
            seekBar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
            seekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    ((EditText) S_gain_map_value.getChildAt(finalI)).setText(i + "");
                    S_gain_map[finalI] = i;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Toast.makeText(MainActivity.this, "RPM" + S_gain_map_lab_[finalI] + "轉速增益設定為: " + S_gain_map[finalI] + "%", Toast.LENGTH_SHORT).show();
                    refresh_time_map();
                }
            });
            S_gain_map_seekbar.addView(seekBar);

            seekBar = new VerticalSeekBar(this);
            seekBar.setMin(Max_Min_value[2]);
            seekBar.setMax(Max_Min_value[3]);
            seekBar.setProgress(T_gain_map[i]);
            seekBar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
            seekBar.setClickable(false);
            seekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    ((EditText) T_gain_map_value.getChildAt(finalI)).setText(i + "");
                    T_gain_map[finalI] = i;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Toast.makeText(MainActivity.this, T_gain_map_lab_[finalI] + "%油門開度增益設定為: " + T_gain_map[finalI] + "%", Toast.LENGTH_SHORT).show();
                    refresh_time_map();
                }
            });
            T_gain_map_seekbar.addView(seekBar);
        }
        for (int i = 0; i < 5; i++) {
            int finalI = i;

            VerticalSeekBar seekBar = new VerticalSeekBar(this);
            seekBar.setMin(Max_Min_value[4]);
            seekBar.setMax(Max_Min_value[5]);
            seekBar.setProgress(G_gain_map[i]);
            seekBar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
            seekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    ((EditText) G_gain_map_value.getChildAt(finalI)).setText(i + "");
                    G_gain_map[finalI] = i;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Toast.makeText(MainActivity.this, G_gain_map_lab_[finalI] + "檔位增益設定為: " + G_gain_map[finalI] + "%", Toast.LENGTH_SHORT).show();
                    refresh_time_map();
                }
            });
            G_gain_map_seekbar.addView(seekBar);
        }
    }

    EditText edittext_cut_time, edittext_cut_smooth_time, edittext_stable_delay_1, edittext_stable_delay_2, edittext_engine_start_delay, edittext_gear_scale_sensitivity, edittext_cut_valve;
    View button_cut_time_up, button_cut_time_down, button_gear_scale_invert;

    void register_page_2_event() {
        G_gain_map_lab = page_2_view.findViewById(R.id.G_gain_map_lab);
        G_gain_map_value = page_2_view.findViewById(R.id.G_gain_map_value);
        G_gain_map_seekbar = page_2_view.findViewById(R.id.G_gain_map_seekbar);

        edittext_cut_time = page_2_view.findViewById(R.id.edittext_cut_time);
        edittext_cut_smooth_time = page_2_view.findViewById(R.id.edittext_cut_smooth_time);
        edittext_stable_delay_1 = page_2_view.findViewById(R.id.edittext_stable_delay_1);
        edittext_stable_delay_2 = page_2_view.findViewById(R.id.edittext_stable_delay_2);
        edittext_engine_start_delay = page_2_view.findViewById(R.id.edittext_engine_start_delay);
        edittext_gear_scale_sensitivity = page_2_view.findViewById(R.id.edittext_gear_scale_sensitivity);
        edittext_cut_valve = page_2_view.findViewById(R.id.edittext_cut_valve);

        button_cut_time_up = page_2_view.findViewById(R.id.button_cut_time_up);
        button_cut_time_down = page_2_view.findViewById(R.id.button_cut_time_down);
        button_gear_scale_invert = page_2_view.findViewById(R.id.button_gear_scale_invert);

        button_cut_time_up.setOnClickListener((view) -> {
            if (++cut_time > 200)
                cut_time = 200;
            edittext_cut_time.setText(cut_time + "");
        });
        button_cut_time_down.setOnClickListener((view) -> {
            if (--cut_time < 10)
                cut_time = 10;
            edittext_cut_time.setText(cut_time + "");
        });

        edittext_cut_time.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_cut_time.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            cut_time = Math.max(Math.min(data, 200), 10);
            ((EditText) view).setText(cut_time + "");
            Toast.makeText(MainActivity.this, "gear_time set : " + cut_time, Toast.LENGTH_SHORT).show();
        });
        edittext_cut_smooth_time.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_cut_smooth_time.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            cut_smooth_time = Math.max(Math.min(data, 200), 0);
            ((EditText) view).setText(cut_smooth_time + "");
            Toast.makeText(MainActivity.this, "cut_smooth_time set : " + cut_smooth_time, Toast.LENGTH_SHORT).show();
        });
        edittext_stable_delay_1.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_stable_delay_1.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            stable_delay[0] = Math.max(Math.min(data, 200), 0);
            ((EditText) view).setText(stable_delay[0] + "");
            Toast.makeText(MainActivity.this, "stable delay set : " + stable_delay[0], Toast.LENGTH_SHORT).show();
        });
        edittext_stable_delay_2.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_stable_delay_2.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            stable_delay[1] = Math.max(Math.min(data, 200), 0);
            ((EditText) view).setText(stable_delay[1] + "");
            Toast.makeText(MainActivity.this, "after cut delay set : " + stable_delay[1], Toast.LENGTH_SHORT).show();
        });

        edittext_engine_start_delay.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_engine_start_delay.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            engine_start_delay = Math.max(Math.min(data / 10, 200), 10);
            ((EditText) view).setText((engine_start_delay * 10) + "");
            Toast.makeText(MainActivity.this, "engine_start_delay set : " + (engine_start_delay * 10), Toast.LENGTH_SHORT).show();
        });

        edittext_gear_scale_sensitivity.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_gear_scale_sensitivity.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            gear_scale_sensitivity = Math.max(Math.min(data, 250), 1);
            ((EditText) view).setText(gear_scale_sensitivity + "");
            Toast.makeText(MainActivity.this, "gear_scale_sensitivity set : " + gear_scale_sensitivity, Toast.LENGTH_SHORT).show();
        });

        edittext_cut_valve.setOnEditorActionListener((view, actionId, event) -> {
            view.clearFocus();
            return false;
        });
        edittext_cut_valve.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus)
                return;
            int data = Integer.parseInt(((EditText) view).getText().toString().trim());
            cut_valve[0] = Math.max(Math.min(data, 200), 10);
            cut_valve[1] = cut_valve[0];
            ((EditText) view).setText(cut_valve[0] + "");
            Toast.makeText(MainActivity.this, "cut_valve set : " + cut_valve[0], Toast.LENGTH_SHORT).show();
        });
        ((ToggleButton) button_gear_scale_invert).setOnCheckedChangeListener((view, isChecked) -> {
            gear_scale_invert = isChecked ? 1 : 0;
            Toast.makeText(MainActivity.this, "檔桿觸發模式:" + (isChecked ? "反向" : "正向"), Toast.LENGTH_SHORT).show();
        });
    }

    void register_page_3_event() {
        S_gain_map_lab = page_3_view.findViewById(R.id.S_gain_map_lab);
        S_gain_map_value = page_3_view.findViewById(R.id.S_gain_map_value);
        S_gain_map_seekbar = page_3_view.findViewById(R.id.S_gain_map_seekbar);

        T_gain_map_lab = page_3_view.findViewById(R.id.T_gain_map_lab);
        T_gain_map_value = page_3_view.findViewById(R.id.T_gain_map_value);
        T_gain_map_seekbar = page_3_view.findViewById(R.id.T_gain_map_seekbar);

        refresh_time_map();
    }

}
