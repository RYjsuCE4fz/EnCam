package de.chaos_darmstadt.schlafwandler.SeCam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

// ----------------------------------------------------------------------

public class SeCam extends Activity {
    private Preview mPreview;
    private Camera mCamera;
    
    private long mEncryptionKeyIds[] = null;
    private String mEncryptedData = null;
    
    @Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
       
    	loadSelectedKeys();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new Preview(this);
        setContentView(mPreview);
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	
    	if (mEncryptionKeyIds == null)
    	{
    		Toast noKeys = Toast.makeText(getApplicationContext(), getString(R.string.noKeySelectedWarning), Toast.LENGTH_LONG);
    		noKeys.show();
    	}
    	
    	mCamera = Camera.open();
    	mPreview.setCamera(mCamera);
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	
    	mCamera.release();
    	mCamera = null;
    	mPreview.setCamera(null);
    	
    	saveSelectedKeys();
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId())
		{
		case R.id.selectKeys:
    		Intent select = new Intent(Apg.Intent.SELECT_PUBLIC_KEYS);
    		select.putExtra(Apg.EXTRA_SELECTION, mEncryptionKeyIds);
    		startActivityForResult(select, Apg.SELECT_PUBLIC_KEYS);
			return true;
		case R.id.preferences:
			Intent pref = new Intent(SeCam.this, Preferences.class);
			startActivity(pref);

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}		
	}
    
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) 
    {
    	if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && mCamera != null)
    	{
    		mCamera.takePicture(null, null, this.onPictureTakenJPEG);
    		return true;
    	}
    	else
    		return super.onKeyDown(keyCode, event);    	
	}
    
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	if (resultCode != Activity.RESULT_OK || data == null)
        {
            return;
        }
    	
    	switch (requestCode)
    	{
    	case Apg.SELECT_PUBLIC_KEYS:
    		mEncryptionKeyIds = data.getLongArrayExtra(Apg.EXTRA_SELECTION);
    		break;
    	case Apg.ENCRYPT_MESSAGE:
    		mEncryptedData = data.getStringExtra(Apg.EXTRA_ENCRYPTED_MESSAGE);
    		saveToFile(mEncryptedData);
    		break;
    	case Apg.SELECT_SECRET_KEY:
    		break;
    	case Apg.DECRYPT_MESSAGE:
    		break;
    	}
    }
    
    boolean testApgAvailability()
    {
    	//TODO: implement me!
    	//PackageInfo pi = context.getPackageManager().getPackageInfo("org.thialfihar.android.apg", 0);
    	return false;
    }
    
    boolean loadSelectedKeys()
    {
    	SharedPreferences settings = getPreferences(MODE_PRIVATE);
    	
    	String serial = settings.getString("publicKeys", "");
    	
    	if (serial == "")
    		return false;
    	
    	String[] sKeys = serial.split("\\s");
    	mEncryptionKeyIds = new long[sKeys.length];
    	
    	for (int i=0;i<sKeys.length;i++)
    	{
    		mEncryptionKeyIds[i] = Long.parseLong(sKeys[i]);    		
    	}
    		
    	return true;
    }
    
    void saveSelectedKeys()
    {
    	StringBuffer serial = new StringBuffer();
    	
    	if (mEncryptionKeyIds == null)
    		return;
    	
    	for (Long key : mEncryptionKeyIds)
    	{
    		serial.append(key);
    	}
    	
    	SharedPreferences settings = getPreferences(MODE_PRIVATE);
    	SharedPreferences.Editor editor = settings.edit();

    	editor.putString("publicKeys", serial.toString());
    	editor.commit();
    }
    
    void saveToFile(String data)
    {
		FileOutputStream f = null;	
		
		try { 
			f = openFileOutput("test.jpg",0);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		try {
			f.write(data.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    boolean saveToFileExternal(String data)
    {
    	return true;
    }
    
    boolean saveToFileInternal(String data)
    {
    	return true;
    }
    
	PictureCallback onPictureTakenJPEG = new PictureCallback() {	
		public void onPictureTaken(byte[] data,Camera cam)
		{

			Intent ear = new Intent(Apg.Intent.ENCRYPT_AND_RETURN);
			
			ear.setType("application/octet-stream");
			ear.putExtra(Apg.EXTRA_DATA, data);
			ear.putExtra(Apg.EXTRA_ENCRYPTION_KEY_IDS, mEncryptionKeyIds);
			
			startActivityForResult(ear, Apg.ENCRYPT_MESSAGE);
			
			cam.startPreview();
			return;
		}
	};    
}

// ----------------------------------------------------------------------

class Preview extends SurfaceView implements SurfaceHolder.Callback {
    SurfaceHolder mHolder;
    Camera mCamera;
    Context context;
    
    Preview(Context context) {
        super(context);
        this.context = context;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    public void setCamera(Camera mCamera)
    {
    	this.mCamera = mCamera;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.

        try {
           mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
    	if (mCamera != null)
        {
    		mCamera.stopPreview();        
    		mCamera.release();
    		mCamera = null;
        }
    }


    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Camera.Parameters parameters = mCamera.getParameters();

        List<Size> sizes = parameters.getSupportedPreviewSizes();
        Size optimalSize = getOptimalPreviewSize(sizes, w, h);
    	parameters.setPreviewSize(optimalSize.width, optimalSize.height);

        switch (display.getRotation())
        {
        case Surface.ROTATION_0:
        	mCamera.setDisplayOrientation(90);
        	break;
        case Surface.ROTATION_90:
        	break;
        case Surface.ROTATION_180:
        	break;
        case Surface.ROTATION_270:
        	mCamera.setDisplayOrientation(180);
        	break;
        }
        
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }

}