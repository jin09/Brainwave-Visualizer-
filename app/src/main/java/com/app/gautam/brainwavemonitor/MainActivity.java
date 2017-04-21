package com.app.gautam.brainwavemonitor;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainACTIVITY";
    // graph plot variables
    private final static int X_RANGE = 50;
    private SimpleXYSeries bp_deltaSeries = null;
    private SimpleXYSeries bp_thetaSeries = null;
    private SimpleXYSeries bp_alphaSeries = null;
    private SimpleXYSeries bp_betaSeries = null;
    private SimpleXYSeries bp_gammaSeries = null;

    // COMM SDK handles
    private TgStreamReader tgStreamReader;
    private BluetoothAdapter mBluetoothAdapter;

    // internal variables
    private boolean bInited = false;
    private boolean bRunning = false;
    private NskAlgoType currentSelectedAlgo;

    // canned data variables
    private short raw_data[] = {0};
    private int raw_data_index = 0;
    private float output_data[];
    private int output_data_count = 0;
    private int raw_data_sec_len = 85;

    // UI components
    private Button headsetButton;
    private Button setAlgosButton;
    private Button startButton;
    private Button stopButton;
    private CheckBox alpha, beta, gamma, delta, theta;
    private TextView attValue;
    private TextView medValue;
    private TextView stateText;
    private TextView sqText;
    private XYPlot plot;
    private NskAlgoSdk nskAlgoSdk;
    private ProgressBar p1,p2;
    private ProgressBar alphaa, betaa, gamaa, deltaa, thetaa;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nskAlgoSdk = new NskAlgoSdk();

        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                //finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
            return;
        }

        alpha = (CheckBox) findViewById(R.id.alpha);
        beta = (CheckBox) findViewById(R.id.beta);
        gamma = (CheckBox) findViewById(R.id.gamma);
        delta = (CheckBox) findViewById(R.id.delta);
        theta = (CheckBox) findViewById(R.id.theta);
        alpha.setChecked(true);
        beta.setChecked(true);
        gamma.setChecked(true);
        delta.setChecked(true);
        theta.setChecked(true);
        alpha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (alpha.isChecked()) {
                    addSeries(plot, bp_alphaSeries, Color.RED);
                } else {
                    plot.removeSeries(bp_alphaSeries);
                }
            }
        });
        beta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (beta.isChecked()) {
                    addSeries(plot, bp_betaSeries, Color.BLUE);
                } else {
                    plot.removeSeries(bp_betaSeries);
                }
            }
        });
        gamma.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (gamma.isChecked()) {
                    addSeries(plot, bp_gammaSeries, Color.YELLOW);
                } else {
                    plot.removeSeries(bp_gammaSeries);
                }
            }
        });
        delta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (delta.isChecked()) {
                    addSeries(plot, bp_deltaSeries, Color.MAGENTA);
                } else {
                    plot.removeSeries(bp_deltaSeries);
                }
            }
        });
        theta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (theta.isChecked()) {
                    addSeries(plot, bp_thetaSeries, Color.GREEN);
                } else {
                    plot.removeSeries(bp_thetaSeries);
                }
            }
        });

        headsetButton = (Button) this.findViewById(R.id.headsetButton);
        setAlgosButton = (Button) this.findViewById(R.id.setAlgosButton);
        startButton = (Button) this.findViewById(R.id.startButton);
        stopButton = (Button) this.findViewById(R.id.stopButton);
        attValue = (TextView) this.findViewById(R.id.attText);
        medValue = (TextView) this.findViewById(R.id.medText);
        stateText = (TextView) this.findViewById(R.id.stateText);
        sqText = (TextView) this.findViewById(R.id.sqText);
        p1 = (ProgressBar) findViewById(R.id.progressBar1);
        p2 = (ProgressBar) findViewById(R.id.progressBar2);
        int red, blue;
        red = Color.RED;
        blue = Color.BLUE;
        p1.getIndeterminateDrawable().setColorFilter(red, PorterDuff.Mode.SRC_IN);
        p1.getProgressDrawable().setColorFilter(red, PorterDuff.Mode.SRC_IN);
        p2.getIndeterminateDrawable().setColorFilter(blue, PorterDuff.Mode.SRC_IN);
        p2.getProgressDrawable().setColorFilter(blue, PorterDuff.Mode.SRC_IN);
        bp_deltaSeries = createSeries("Delta");
        bp_thetaSeries = createSeries("Theta");
        bp_alphaSeries = createSeries("Alpha");
        bp_betaSeries = createSeries("Beta");
        bp_gammaSeries = createSeries("Gamma");
        headsetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                output_data_count = 0;
                output_data = null;
                raw_data = new short[512];
                raw_data_index = 0;
                headsetButton.setEnabled(false);
                startButton.setEnabled(false);
                tgStreamReader = new TgStreamReader(mBluetoothAdapter, callback);

                if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                    // Prepare for connecting
                    tgStreamReader.stop();
                    tgStreamReader.close();
                }
                tgStreamReader.connect();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bRunning == false) {
                    nskAlgoSdk.NskAlgoStart(false);
                } else {
                    nskAlgoSdk.NskAlgoPause();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nskAlgoSdk.NskAlgoStop();
            }
        });

        setAlgosButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // check selected algos
                int algoTypes = 0;// = NskAlgoType.NSK_ALGO_TYPE_CR.value;
                startButton.setEnabled(false);
                stopButton.setEnabled(false);
                currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_INVALID;
                attValue.setText("--");
                medValue.setText("--");
                stateText.setText("");
                sqText.setText("");
                algoTypes += NskAlgoType.NSK_ALGO_TYPE_MED.value;

                algoTypes += NskAlgoType.NSK_ALGO_TYPE_ATT.value;

                algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;

                algoTypes += NskAlgoType.NSK_ALGO_TYPE_BP.value;

                if (algoTypes == 0) {
                    showDialog("Please select at least one algorithm");
                } else {
                    if (bInited) {
                        nskAlgoSdk.NskAlgoUninit();
                        bInited = false;
                    }
                    int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
                    if (ret == 0) {
                        bInited = true;
                    }

                    Log.d(TAG, "NSK_ALGO_Init() " + ret);
                    String sdkVersion = "SDK ver.: " + nskAlgoSdk.NskAlgoSdkVersion();

                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_ATT.value) != 0) {
                        sdkVersion += "\nATT ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_ATT.value);
                    }
                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_MED.value) != 0) {
                        sdkVersion += "\nMED ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_MED.value);
                    }
                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BLINK.value) != 0) {
                        sdkVersion += "\nBlink ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BLINK.value);
                    }
                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BP.value) != 0) {
                        sdkVersion += "\nEEG Bandpower ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BP.value);
                    }
                    showToast(sdkVersion, Toast.LENGTH_LONG);
                }
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(int level) {
                //Log.d(TAG, "NskAlgoSignalQualityListener: level: " + level);
                final int fLevel = level;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[fLevel].toString();
                        sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {
                String stateStr = "";
                String reasonStr = "";
                for (NskAlgoState s : NskAlgoState.values()) {
                    if (s.value == state) {
                        stateStr = s.toString();
                    }
                }
                for (NskAlgoState r : NskAlgoState.values()) {
                    if (r.value == reason) {
                        reasonStr = r.toString();
                    }
                }
                Log.d(TAG, "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        stateText.setText(finalStateStr);

                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            bRunning = true;
                            startButton.setText("Pause");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            bRunning = false;
                            raw_data = null;
                            raw_data_index = 0;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            headsetButton.setEnabled(true);
                            //cannedButton.setEnabled(true);
                            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                                // Prepare for connecting
                                tgStreamReader.stop();
                                tgStreamReader.close();
                            }

                            output_data_count = 0;
                            output_data = null;

                            System.gc();
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            bRunning = false;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            bRunning = true;
                            startButton.setText("Start");
                            startButton.setEnabled(false);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            bRunning = false;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                        }
                    }
                });
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(final int level) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[level].toString();
                        sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnBPAlgoIndexListener(new NskAlgoSdk.OnBPAlgoIndexListener() {
            @Override
            public void onBPAlgoIndex(float delta, float theta, float alpha, float beta, float gamma) {
                Log.d(TAG, "NskAlgoBPAlgoIndexListener: BP: D[" + delta + " dB] T[" + theta + " dB] A[" + alpha + " dB] B[" + beta + " dB] G[" + gamma + "]");
                final int alp = (int)alpha;
                final int bet = (int)beta;
                final int gam = (int)gamma;
                final int del = (int)delta;
                final int thet = (int)theta;

                final float fDelta = delta, fTheta = theta, fAlpha = alpha, fBeta = beta, fGamma = gamma;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        AddValueToPlot(bp_deltaSeries, fDelta);
                        AddValueToPlot(bp_thetaSeries, fTheta);
                        AddValueToPlot(bp_alphaSeries, fAlpha);
                        AddValueToPlot(bp_betaSeries, fBeta);
                        AddValueToPlot(bp_gammaSeries, fGamma);
                    }
                });
            }
        });

        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
            @Override
            public void onAttAlgoIndex(final int value) {
                Log.d(TAG, "NskAlgoAttAlgoIndexListener: Attention:" + value);
                String attStr = "[" + value + "]";
                final String finalAttStr = attStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        attValue.setText(finalAttStr);
                        //TODO
                        //syncref.setValue(String.valueOf(value));
                        p1.setProgress(value);
                    }
                });
            }
        });

        nskAlgoSdk.setOnMedAlgoIndexListener(new NskAlgoSdk.OnMedAlgoIndexListener() {
            @Override
            public void onMedAlgoIndex(final int value) {
                Log.d(TAG, "NskAlgoMedAlgoIndexListener: Meditation:" + value);
                String medStr = "[" + value + "]";
                final String finalMedStr = medStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        medValue.setText(finalMedStr);
                        p2.setProgress(value);
                    }
                });
            }
        });

        nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
            @Override
            public void onEyeBlinkDetect(int strength) {
                Log.d(TAG, "NskAlgoEyeBlinkDetectionListener: Eye blink detected: " + strength);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //blinkImage.setImageResource(R.mipmap.led_on);
                        Toast.makeText(getApplication(), "YOU BLINKED \uD83D\uDE09", Toast.LENGTH_SHORT).show();
                        Timer timer = new Timer();

                        timer.schedule(new TimerTask() {
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //blinkImage.setImageResource(R.mipmap.led_off);
                                    }
                                });
                            }
                        }, 500);
                    }
                });
            }
        });

        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.xyplot);
        plot.setVisibility(View.INVISIBLE);
        bpPress();
    }

    private void bpPress(){
        removeAllSeriesFromPlot();
        setupPlot(-20, 20, "EEG Bandpower");
        addSeries(plot, bp_deltaSeries, Color.MAGENTA);
        addSeries(plot, bp_thetaSeries, Color.GREEN);
        addSeries(plot, bp_alphaSeries, Color.RED);
        addSeries(plot, bp_betaSeries, Color.BLUE);
        addSeries(plot, bp_gammaSeries, Color.YELLOW);
        plot.redraw();
        currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_BP;
    }

    private void removeAllSeriesFromPlot () {
        if (bp_deltaSeries != null) {
            plot.removeSeries(bp_deltaSeries);
        }
        if (bp_thetaSeries != null) {
            plot.removeSeries(bp_thetaSeries);
        }
        if (bp_alphaSeries != null) {
            plot.removeSeries(bp_alphaSeries);
        }
        if (bp_betaSeries != null) {
            plot.removeSeries(bp_betaSeries);
        }
        if (bp_gammaSeries != null) {
            plot.removeSeries(bp_gammaSeries);
        }
        System.gc();
    }

    private void clearAllSeries () {
        if (bp_deltaSeries != null) {
            plot.removeSeries(bp_deltaSeries);
            bp_deltaSeries = null;
        }
        if (bp_thetaSeries != null) {
            plot.removeSeries(bp_thetaSeries);
            bp_thetaSeries = null;
        }
        if (bp_alphaSeries != null) {
            plot.removeSeries(bp_alphaSeries);
            bp_alphaSeries = null;
        }
        if (bp_betaSeries != null) {
            plot.removeSeries(bp_betaSeries);
            bp_betaSeries = null;
        }
        if (bp_gammaSeries != null) {
            plot.removeSeries(bp_gammaSeries);
            bp_gammaSeries = null;
        }
        plot.setVisibility(View.INVISIBLE);
        System.gc();
    }

    private XYPlot setupPlot (Number rangeMin, Number rangeMax, String title) {
        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.xyplot);

        if ((rangeMax.intValue() - rangeMin.intValue()) < 10) {
            plot.setRangeStepValue((rangeMax.intValue() - rangeMin.intValue() + 1));
        } else {
            plot.setRangeStepValue(11);
        }
        plot.setRangeBoundaries(rangeMin.intValue(), rangeMax.intValue(), BoundaryMode.FIXED);
        plot.setPlotPadding(0, 0, 0, 0);
        plot.setTitle(title);
        plot.setVisibility(View.VISIBLE);
        return plot;
    }

    private SimpleXYSeries createSeries (String seriesName) {
        // Turn the above arrays into XYSeries':
        SimpleXYSeries series = new SimpleXYSeries(
                null,          // SimpleXYSeries takes a List so turn our array into a List
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
                seriesName);                             // Set the display title of the series

        series.useImplicitXVals();

        return series;
    }

    private SimpleXYSeries addSeries (XYPlot plot, SimpleXYSeries series, int colour) {

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter seriesFormat = new LineAndPointFormatter(colour, null, null, null);
        seriesFormat.setPointLabelFormatter(null);
        seriesFormat.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        seriesFormat.getLinePaint().setStrokeWidth(7);
        //seriesFormat.configure(getApplicationContext(), null);
        seriesFormat.setVertexPaint(null);
        series.useImplicitXVals();

        // add a new series' to the xyplot:
        plot.addSeries(series, seriesFormat);

        return series;
    }

    private int gcCount = 0;
    private void AddValueToPlot (SimpleXYSeries series, float value) {
        if (series.size() >= X_RANGE) {
            series.removeFirst();
        }
        Number num = value;
        series.addLast(null, num);
        plot.redraw();
        gcCount++;
        if (gcCount >= 20) {
            System.gc();
            gcCount = 0;
        }
    }


    private short[] readData(InputStream is, int size) {
        short data[] = new short[size];
        int lineCount = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            while (lineCount < size) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    Log.d(TAG, "lineCount=" + lineCount);
                    break;
                }
                data[lineCount] = Short.parseShort(line);
                lineCount++;
            }
            Log.d(TAG, "lineCount=" + lineCount);
        } catch (IOException e) {

        }
        return data;
    }

    @Override
    public void onBackPressed() {
        nskAlgoSdk.NskAlgoUninit();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Button startButton = (Button) findViewById(R.id.startButton);
                            startButton.setEnabled(true);
                        }

                    });

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.

                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG, "onRecordFail: " + flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short) data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

    };

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    private void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
