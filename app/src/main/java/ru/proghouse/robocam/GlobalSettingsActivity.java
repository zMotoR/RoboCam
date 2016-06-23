package ru.proghouse.robocam;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class GlobalSettingsActivity extends AppCompatActivity {
    private Button buttonServer = null;
    private Button buttonRobot = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_settings);

        buttonServer = (Button)findViewById(R.id.buttonServer);
        buttonServer.setTransformationMethod(null);

        buttonRobot = (Button)findViewById(R.id.buttonRobot);
        buttonRobot.setTransformationMethod(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_global_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onCameraButtonClick(View v){
        Intent intent = new Intent(this, ServerSettingsActivity.class);
        startActivity(intent);
    }

    public void onRobotButtonClick(View v){
        Intent intent = new Intent(this, RobotSettingsListActivity.class);
        startActivity(intent);
    }

    public void onCancelButtonClick(View v){
        finish();
    }
}
