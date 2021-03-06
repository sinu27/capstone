package com.example.sinu.capstone;

import android.animation.Animator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    CustomExpandableListViewAdapter listAdapter;
    ExpandableListView expListView;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;
    //Firebase
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;

    //DB
    String myJSON;
    String myJSON_Ordered;

    private static final String TAG_RESULTS = "result";
    private static final String TAG_TYPE = "type";
    private static final String TAG_NAME = "name";
    private static final String TAG_PRICE = "price";
    private static final String TAG_Order_NAME = "menu_name";
    private static final String TAG_Order_PRICE = "menu_price";
    private static final String TAG_Order_COUNT = "count";
    private static final String TAG_Order_RESULTS = "result";

    JSONArray menu = null;

    ArrayList al_type = new ArrayList();
    ArrayList al_name = new ArrayList();
    ArrayList al_price = new ArrayList();

    ArrayList<String[]> ordered = new ArrayList<String[]>();
    ArrayList orderedMenu = new ArrayList();
    ArrayList orderedPrice = new ArrayList();

    orderData chatData;

    FloatingActionButton fab;
    LinearLayout fabLayout1;
    View fabBGLayout;
    boolean isFABOpen = false;

    String ID, tableno;
    int TotalToPay;
    CoordinatorLayout corlay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        corlay = (CoordinatorLayout)findViewById(R.id.coordinatorlayout);
        //intent from Login
        Intent intent = getIntent();
        ID = intent.getStringExtra("ID");
        tableno = intent.getStringExtra("tableno");

        //FAB
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().hide();

        fabLayout1 = (LinearLayout) findViewById(R.id.fabLayout1);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fabBGLayout = findViewById(R.id.fabBGLayout);

        //DB에서 받아오기 (결제)
        getOrderedData("http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/orderlist.php");

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isFABOpen) {
                    showFABMenu();
                } else {
                    closeFABMenu();
                }
            }
        });
        fabBGLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeFABMenu();
            }
        });

        //결제하기
        Button btnPay = (Button)findViewById(R.id.paybtn);
        btnPay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView tvPay = (TextView)findViewById(R.id.list);
                if(tvPay.getText().length()==18){
                    Snackbar.make(corlay,"주문내역이 없습니다.", Snackbar.LENGTH_LONG).show();
                    closeFABMenu();
                }else {
                    //현재시각
                    long time = System.currentTimeMillis();
                    SimpleDateFormat dateTime = new SimpleDateFormat("YYYY-MM-dd");
                    String date = dateTime.format(new Date(time));
                    SimpleDateFormat secTime = new SimpleDateFormat("HH:mm:ss");
                    String sec = secTime.format(new Date(time));

                    InsertData2 taskPay = new InsertData2();
                    String serverURL = "http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/pay.php";
                    String postParameters = "TABLE_NO=" + tableno.toString() + "&HOST_ID=" + ID + "&DATE=" + date + "&TIME=" + sec;
                    taskPay.execute(serverURL, postParameters);

                    Snackbar.make(corlay, TotalToPay + "원 결제가 완료되었습니다", Snackbar.LENGTH_LONG).show();

                    closeFABMenu();
                    getOrderedData("http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/orderlist.php");

                    //현재시각
                    long time_pay = System.currentTimeMillis();
                    SimpleDateFormat dayTime = new SimpleDateFormat("MMddHHmmssSS");
                    String getTime = dayTime.format(new Date(time_pay));
                    //매니저앱 결제
                    String tableString = tableno.toString() + "번";
                    TradeData dataToPay = new TradeData(getTime, tableString, "1", "0", "0", "0", String.valueOf(TotalToPay));
                    databaseReference.child("store1_trade").child(getTime).setValue(dataToPay);  // 기본 database 하위 message라는 child에 chatData를 list로 만들기
                    System.out.println(getTime + "  " + tableno.toString() + "  " + String.valueOf(TotalToPay));
                }
            }
        });

        //DB
        getData("http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/menu.php");

        // get the listview
        expListView = (ExpandableListView) findViewById(R.id.expandableListView);

        //firebase
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference();

        ///
        Button btnComplete = (Button) findViewById(R.id.btnOrder);
        btnComplete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(orderedMenu.size()==0){
                    Snackbar.make(corlay, "주문내역이 없습니다.", Snackbar.LENGTH_LONG).show();
                }else {
                    /** 원본 데이터 유형 */
                    ArrayList<String> itemList = new ArrayList<String>();
                    /** 원본 데이터 유형별 중복개수 */
                    ArrayList<Integer> cntList = new ArrayList<Integer>();
                    //1. 데이터 유형 및 개수를 설정한다.
                    for (int index = 0; index < orderedMenu.size(); index++) {
                        //item이 등록되었는지 확인한다.
                        //1.1 등록되지 않았을 때 처리
                        if (!itemList.contains(orderedMenu.get(index).toString())) {

                            //1.1.1 item을 itemList에 추가한다.
                            itemList.add(orderedMenu.get(index).toString());

                            //1.1.2 item이 몇개 들어있는지 세어서 cntList에 추가한다.
                            int cnt = 0;
                            for (int searchIndex = index; searchIndex < orderedMenu.size(); searchIndex++) {
                                if (orderedMenu.get(index).toString() == orderedMenu.get(searchIndex).toString()) {
                                    cnt++;
                                }
                            }
                            cntList.add(cnt);
                        } else continue;
                    }


                    String Menu_name = "";
                    String count = "";
                    String Price_list = "";
                    String total_string = "";
                    int totalCount = 0;
                    for (int i = 0; i < itemList.size(); i++) {
                        Menu_name += itemList.get(i) + "\n";
                        count += cntList.get(i) + "개\n";
                        orderedPrice.add(ordered.get(i)[1]);
                        Price_list += orderedPrice.get(i) + "\n";
                        total_string += Integer.parseInt(orderedPrice.get(i).toString()) * cntList.get(i) + "\n";

                        totalCount += cntList.get(i);
                        //DB
                        InsertData2 task = new InsertData2();
                        String serverURL = "http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/order2.php";
                        String postParameters = "TABLE_NO=" + tableno.toString() + "&MENU_NAME=" + itemList.get(i).toString() + "&MENU_PRICE=" + orderedPrice.get(i).toString() + "&COUNT=" + cntList.get(i).toString();
                        task.execute(serverURL, postParameters);
                    }
                    //현재시각
                    long time = System.currentTimeMillis();
                    SimpleDateFormat dayTime = new SimpleDateFormat("MMddHHmmssSS");
                    String str = dayTime.format(new Date(time));

                    getOrderedData("http://ec2-13-125-34-12.ap-northeast-2.compute.amazonaws.com/qble/orderlist.php");


                    chatData = new orderData(str, str, tableno + "번", Menu_name, count, Price_list, total_string, Color.RED, 1);  // (날짜,table번호,메뉴이름,수량)
                    databaseReference.child("store1").child(str).setValue(chatData);  // 기본 database 하위 message라는 child에 chatData를 list로 만들기
                    itemList.clear();
                    cntList.clear();
                    orderedMenu.clear();
                    orderedPrice.clear();
                    ordered.clear();
                    Snackbar.make(corlay, totalCount + "개의 메뉴가 주문 완료되었습니다", Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showFABMenu() {
        isFABOpen = true;
        fabLayout1.setVisibility(View.VISIBLE);

        fabBGLayout.setVisibility(View.VISIBLE);

        fab.animate().rotationBy(180);
        fabLayout1.animate().translationX(0);
    }

    private void closeFABMenu() {
        isFABOpen = false;
        fabBGLayout.setVisibility(View.GONE);
        fab.animate().rotationBy(-180);

        fabLayout1.animate().translationX(1000).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (!isFABOpen) {
                    fabLayout1.setVisibility(View.GONE);
                }

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
    }


    /*
    * Preparing the list data
    */
    private void prepareListData() {
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();

        // Adding child data
        listDataHeader.add("추천메뉴");
        listDataHeader.add("한식");
        listDataHeader.add("일식");
        listDataHeader.add("튀김");
        listDataHeader.add("국물");
        List<String> recom = new ArrayList<String>(), hansik = new ArrayList<String>(), ilsik = new ArrayList<String>(), fries = new ArrayList<String>(),
                stew = new ArrayList<String>();

        for (int i = 0; i < al_type.size(); i++) {
            if (al_type.get(i).toString().equals("추천메뉴")) {
                recom.add(al_name.get(i).toString() + "|" + al_price.get(i).toString());
            } else if (al_type.get(i).toString().equals("한식")) {
                hansik.add(al_name.get(i).toString() + "|" + al_price.get(i).toString());
            } else if (al_type.get(i).toString().equals("일식")) {
                ilsik.add(al_name.get(i).toString() + "|" + al_price.get(i).toString());
            } else if (al_type.get(i).toString().equals("튀김")) {
                fries.add(al_name.get(i).toString() + "|" + al_price.get(i).toString());
            } else if (al_type.get(i).toString().equals("국물")) {
                stew.add(al_name.get(i).toString() + "|" + al_price.get(i).toString());
            }
        }

        listDataChild.put(listDataHeader.get(0), recom);// Header, Child data
        listDataChild.put(listDataHeader.get(1), hansik);
        listDataChild.put(listDataHeader.get(2), ilsik);
        listDataChild.put(listDataHeader.get(3), fries);
        listDataChild.put(listDataHeader.get(4), stew);
    }

    @Override
    public void onBackPressed() {
        if (!isFABOpen) {
            //   this.super.onBackPressed();
        } else {
            closeFABMenu();
        }
    }

    class CustomExpandableListViewAdapter extends BaseExpandableListAdapter {

        private Context _context;
        private List<String> _listDataHeader; // header titles
        // child data in format of header title, child title
        private HashMap<String, List<String>> _listDataChild;

        public CustomExpandableListViewAdapter(Context context, List<String> listDataHeader,
                                               HashMap<String, List<String>> listChildData) {

            this._context = context;
            this._listDataHeader = listDataHeader;
            this._listDataChild = listChildData;
        }

        @Override
        public Object getChild(int groupPosition, int childPosititon) {
            return this._listDataChild.get(this._listDataHeader.get(groupPosition))
                    .get(childPosititon);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {


            final String Text = (String) getChild(groupPosition, childPosition);
            String[] result = Text.split("\\|");
            final String childText = result[0];
            final String priceText = result[1];

            if (convertView == null) {
                LayoutInflater infalInflater = (LayoutInflater) this._context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = infalInflater.inflate(R.layout.childitem, null);
            }

            TextView txtListChild = (TextView) convertView
                    .findViewById(R.id.childTV);

            txtListChild.setText(childText);

            TextView txtPriceChild = (TextView) convertView
                    .findViewById(R.id.priceTV);

            txtPriceChild.setText(priceText);

            Button button1 = (Button) convertView.findViewById(R.id.button);
            button1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ordered.add(new String[]{childText, priceText});
                    orderedMenu.add(childText.toString());
                }
            });
            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return this._listDataChild.get(this._listDataHeader.get(groupPosition))
                    .size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return this._listDataHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return this._listDataHeader.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            String headerTitle = (String) getGroup(groupPosition);
            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.parentitem, null);

            }
            TextView lblListHeader = (TextView) convertView.findViewById(R.id.parentTV);
            lblListHeader.setTypeface(null, Typeface.BOLD);
            lblListHeader.setText(headerTitle);

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    ////////////////////////////////DB//////////////////////////////////
    //<주문>
    protected void showList() {
        try {
            JSONObject jsonObj = new JSONObject(myJSON);
            menu = jsonObj.getJSONArray(TAG_RESULTS);

            for (int i = 0; i < menu.length(); i++) {
                JSONObject c = menu.getJSONObject(i);
                String type = c.getString(TAG_TYPE);
                String name = c.getString(TAG_NAME);
                String price = c.getString(TAG_PRICE);
                al_type.add(type);
                al_name.add(name);
                al_price.add(price);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //onclick
        prepareListData();
        listAdapter = new CustomExpandableListViewAdapter(this, listDataHeader, listDataChild);

        // setting list adapter
        expListView.setAdapter(listAdapter);
    }

    //<결제>
    protected void showOrderedList() {
        String orderedData ="";
        TotalToPay = 0;
        try {
            JSONObject jsonObj = new JSONObject(myJSON_Ordered);
            menu = jsonObj.getJSONArray(TAG_Order_RESULTS);

            for (int i = 0; i < menu.length(); i++) {
                JSONObject c = menu.getJSONObject(i);
                String name = c.getString(TAG_Order_NAME);
                String price = c.getString(TAG_Order_PRICE);
                String count = c.getString(TAG_Order_COUNT);

                orderedData += name+"\t\t\t\t"+price+"\t\t\t\t"+count+"\n";
                TotalToPay+=(Integer.parseInt(price)*Integer.parseInt(count));

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        TextView tvPay = (TextView)findViewById(R.id.list);
        if(orderedData.isEmpty()){
         tvPay.setText("주문내역이 없습니다.     "+"\n\n");
        }else {
            tvPay.setText(orderedData);
        }
    }

    public void getData(String url) {
        class GetDataJSON extends AsyncTask<String, Void, String> {

            @Override
            protected String doInBackground(String... params) {

                String uri = params[0];

                BufferedReader bufferedReader = null;
                try {
                    URL url = new URL(uri);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    StringBuilder sb = new StringBuilder();

                    bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));

                    String json;
                    while ((json = bufferedReader.readLine()) != null) {
                        sb.append(json + "\n");
                    }

                    return sb.toString().trim();

                } catch (Exception e) {
                    return null;
                }


            }

            @Override
            protected void onPostExecute(String result) {
                myJSON = result;
                showList();
            }
        }
        GetDataJSON g = new GetDataJSON();
        g.execute(url);
    }

    public void getOrderedData(String url) {
        class GetDataJSON extends AsyncTask<String, Void, String> {

            @Override
            protected String doInBackground(String... params) {

                String uri = params[0];

                BufferedReader bufferedReader = null;
                try {
                    URL url = new URL(uri);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    StringBuilder sb = new StringBuilder();

                    bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));

                    String json;
                    while ((json = bufferedReader.readLine()) != null) {
                        sb.append(json + "\n");
                    }

                    return sb.toString().trim();

                } catch (Exception e) {

                    return null;
                }


            }

            @Override
            protected void onPostExecute(String result) {
                myJSON_Ordered = result;
                showOrderedList();
            }
        }
        GetDataJSON g = new GetDataJSON();
        g.execute(url);
    }

    class InsertData2 extends AsyncTask<String, Void, String> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            progressDialog = ProgressDialog.show(MainActivity.this,
                    "Please Wait", null, true, true);
        }


        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressDialog.dismiss();
            if (result.equals("성공")) {
            }
        }


        @Override
        protected String doInBackground(String... params) {
            String serverURL = (String) params[0];
            String postParameters = (String) params[1];

            try {

                URL url = new URL(serverURL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();


                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.connect();

                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(postParameters.getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();

                int responseStatusCode = httpURLConnection.getResponseCode();

                InputStream inputStream;
                if (responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                } else {
                    inputStream = httpURLConnection.getErrorStream();
                }


                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                StringBuilder sb = new StringBuilder();
                String line = null;

                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }

                bufferedReader.close();

                return sb.toString();

            } catch (Exception e) {
                return new String("Error: " + e.getMessage());
            }
        }
    }
}