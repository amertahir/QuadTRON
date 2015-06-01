package edu.umbc.cmsc691.updave;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import com.digi.xbee.api.DigiMeshDevice;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBee64BitAddress;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.utils.ByteUtils;
import com.esri.runtime.ArcGISRuntime;
import com.esri.toolkit.overlays.NavigatorOverlay;
import com.esri.toolkit.overlays.ScaleBarOverlay;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol.Style;
import com.esri.core.symbol.Symbol;
import com.esri.map.GraphicsLayer;
import com.esri.map.JMap;
import com.esri.map.LocationOnMap;
import com.esri.map.MapEvent;
import com.esri.map.MapEventListenerAdapter;
import com.esri.map.MapOptions;
import com.esri.map.MapOptions.MapType;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JButton;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;

import javax.swing.JRadioButton;
import javax.swing.AbstractAction;
import javax.swing.Action;

import java.util.Timer;
import javax.swing.SwingConstants;
import java.awt.Font;

public class MainWindow implements IDataReceiveListener {

	// XBee stuff
	/* Constants */
    // Port where the XBee module is connected to.
    private static final String PORT = "COM12";
    // Baud rate of the XBee module.
    private static final int BAUD_RATE = 115200;
    // XBee MACs
    //public static String baseStationMAC = "0013A200408BA39A";
    //public static String uav1MAC = "0013A20040A5442D";
    public static String baseStationMAC = "0013A20040C8D2F2";
    public static String uav1MAC = "0013A20040E83D64";
    public static String broadcastMAC = XBee64BitAddress.BROADCAST_ADDRESS.toString();
    // XBee MTU
    public static final int xbeeMTU = 100;

    private DigiMeshDevice xbeeRadio;
    private int xbeePacketLen;
    private byte[] xbeePacketBuffer;
	
	// UI & Map stuff
	private JFrame frmGroundControlStation;
	private JMap map;
	private boolean isMapLoaded;
	private GraphicsLayer graphicsLayer;
	private Symbol uavSymbol;
	private int uav1SymbolId;
	private static final double mapDefaultLat = 39.256206, mapDefaultLong = -76.710170;
	private static final int mapDefaultZoom = 17;
	private static final String IMAGE_QUADCOPTER = "resources/quadcopter-small.png";
	private static final String IMAGE_PLACEHOLDER = "resources/image-placeholder.png";
	// Define the spatial reference of our latitude-longitude points, WGS84
	private static SpatialReference wgs84 = SpatialReference.create(4326);
	private JLabel imgFrame;
	public String thermalImagePalette = "grayscale";
	public boolean mapRecentered = false;
	private JLabel lblHeartbeatsValue;
	private JLabel lblAirspeedValue;
	private JLabel lblGroundSpeedValue;
	private JLabel lblArmedValue;
	private JLabel lblDetectedValue;
	private JLabel lblAltitudeValue;
	/**
	 * @wbp.nonvisual location=50,80
	 */
	private final Timer timer = new Timer();

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainWindow window = new MainWindow();
					window.frmGroundControlStation.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public MainWindow() {
		initialize();
	}
	
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmGroundControlStation = new JFrame();
		frmGroundControlStation.setTitle("Ground Control Station");
		frmGroundControlStation.setBounds(100, 100, 1280, 720);
		frmGroundControlStation.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// Radio setup and connection
		xbeePacketLen = 0;
		xbeePacketBuffer = new byte[1024 * 200];
		Util.resetBuffer(xbeePacketBuffer);
        xbeeRadio = new DigiMeshDevice(PORT, BAUD_RATE, 8, 1, 0, 0);
		try {
            xbeeRadio.open();
            xbeeRadio.addDataListener(this);
            System.out.println("Radio initialized");
        } catch (XBeeException e) {
            System.out.println("Can't connect to radio");
            e.printStackTrace();
            //System.exit(1);
        }
		
		// Maps runtime init
		MapsHelper.initLic();
		
		// Map stuff
		
		isMapLoaded = false;
	    // Using MapOptions allows for a common online basemap to be chosen
		// Set initial map position
	    MapOptions mapOptions = new MapOptions(MapType.HYBRID, mapDefaultLat, mapDefaultLong, mapDefaultZoom);
	    // Add a scale bar overlay to the map
	    ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay();
	    // Add a navigator overlay to the map (top-left corner)
	    NavigatorOverlay navigatorOverlay = new NavigatorOverlay();
	    navigatorOverlay.setLocation(LocationOnMap.TOP_LEFT);
	    // Add graphics layer to draw UAVs and other graphics
	    graphicsLayer = new GraphicsLayer();
	    
	    // Create UAV symbol
	    uavSymbol = makeUAVSymbol();
	    GridBagLayout gridBagLayout = new GridBagLayout();
	    gridBagLayout.columnWidths = new int[]{1264, 205, 0};
	    gridBagLayout.rowHeights = new int[]{681, 0};
	    gridBagLayout.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
	    gridBagLayout.rowWeights = new double[]{1.0, Double.MIN_VALUE};
	    frmGroundControlStation.getContentPane().setLayout(gridBagLayout);
	    map = new JMap(mapOptions);
	    // Hide Esri logo
	    map.setShowingEsriLogo(false);
	    map.addMapOverlay(scaleBarOverlay);
	    map.addMapOverlay(navigatorOverlay);
	    map.getLayers().add(graphicsLayer);
	    
	    // Set up map initialization listener
	    map.addMapEventListener(new MapEventListenerAdapter() {
	        @Override
	        public void mapReady(MapEvent event) {
	        	isMapLoaded = true;
	        	// draw UAV1 (just an example)
	        	Point point = (Point)GeometryEngine.project(new Point(mapDefaultLong, mapDefaultLat), wgs84, map.getSpatialReference());
	        	Graphic uav1Graphic = new Graphic(point, uavSymbol);
	        	uav1SymbolId = graphicsLayer.addGraphic(uav1Graphic);
	        }
	      });
	    GridBagConstraints gbc_map = new GridBagConstraints();
	    gbc_map.insets = new Insets(0, 0, 0, 5);
	    gbc_map.fill = GridBagConstraints.BOTH;
	    gbc_map.gridx = 0;
	    gbc_map.gridy = 0;
	    frmGroundControlStation.getContentPane().add(map, gbc_map);
	    
	    JPanel panel = new JPanel();
	    panel.setLayout(null);
	    GridBagConstraints gbc_panel = new GridBagConstraints();
	    gbc_panel.fill = GridBagConstraints.BOTH;
	    gbc_panel.gridx = 1;
	    gbc_panel.gridy = 0;
	    frmGroundControlStation.getContentPane().add(panel, gbc_panel);
	    
	    imgFrame = new JLabel();
	    imgFrame.setLocation(0, 5);
	    imgFrame.setSize(200, 150);
	    ImageIcon img1 = new ImageIcon(IMAGE_PLACEHOLDER);
	    imgFrame.setIcon(img1);
	    panel.add(imgFrame);
	    
	    JButton btnThermalCameraFfc = new JButton("Thermal Camera FFC");
	    btnThermalCameraFfc.setBounds(10, 595, 180, 25);
	    btnThermalCameraFfc.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		GCSSendMessage(uav1MAC, "TCAM_FFC");
	    	}
	    });
	    panel.add(btnThermalCameraFfc);
	    
	    JButton btnStopMessaging = new JButton("Stop Messaging");
	    btnStopMessaging.setBounds(10, 645, 180, 25);
	    btnStopMessaging.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		GCSSendMessage(uav1MAC, "STOP");
	    	}
	    });
	    panel.add(btnStopMessaging);
	    
	    JRadioButton rdbtnColor = new JRadioButton("Color");
	    rdbtnColor.setBounds(10, 5, 80, 23);
	    rdbtnColor.setActionCommand("color");
	    rdbtnColor.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		thermalImagePalette = e.getActionCommand();
	    	}
	    });
	    rdbtnColor.setVisible(false);
	    panel.add(rdbtnColor);
	    
	    JRadioButton rdbtnGrayscale = new JRadioButton("Grayscale");
	    rdbtnGrayscale.setSelected(true);
	    rdbtnGrayscale.setBounds(102, 5, 98, 23);
	    rdbtnGrayscale.setActionCommand("grayscale");
	    rdbtnGrayscale.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		thermalImagePalette = e.getActionCommand();
	    	}
	    });
	    rdbtnGrayscale.setVisible(false);
	    panel.add(rdbtnGrayscale);
	    
	    ButtonGroup thermalImagePaletteButtonGroup = new ButtonGroup();
	    thermalImagePaletteButtonGroup.add(rdbtnColor);
	    thermalImagePaletteButtonGroup.add(rdbtnGrayscale);
	    
	    JLabel lblAirspeed = new JLabel("Airspeed");
	    lblAirspeed.setHorizontalAlignment(SwingConstants.CENTER);
	    lblAirspeed.setBounds(60, 240, 80, 14);
	    panel.add(lblAirspeed);
	    
	    JLabel lblGroundspeed = new JLabel("Groundspeed");
	    lblGroundspeed.setHorizontalAlignment(SwingConstants.CENTER);
	    lblGroundspeed.setBounds(60, 320, 80, 14);
	    panel.add(lblGroundspeed);
	    
	    JLabel lblHeartbeats = new JLabel("Heartbeats");
	    lblHeartbeats.setHorizontalAlignment(SwingConstants.CENTER);
	    lblHeartbeats.setBounds(60, 170, 80, 14);
	    panel.add(lblHeartbeats);
	    
	    JLabel lblAltitude = new JLabel("Altitude");
	    lblAltitude.setHorizontalAlignment(SwingConstants.CENTER);
	    lblAltitude.setBounds(60, 400, 80, 14);
	    panel.add(lblAltitude);
	    
	    JLabel lblUavArmed = new JLabel("UAV Armed");
	    lblUavArmed.setHorizontalAlignment(SwingConstants.CENTER);
	    lblUavArmed.setBounds(60, 480, 80, 14);
	    panel.add(lblUavArmed);
	    
	    lblHeartbeatsValue = new JLabel("--");
	    lblHeartbeatsValue.setFont(new Font("Tahoma", Font.BOLD, 22));
	    lblHeartbeatsValue.setForeground(new Color(25, 25, 112));
	    lblHeartbeatsValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblHeartbeatsValue.setBounds(20, 190, 160, 30);
	    panel.add(lblHeartbeatsValue);
	    
	    lblAirspeedValue = new JLabel("--");
	    lblAirspeedValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblAirspeedValue.setForeground(new Color(128, 0, 128));
	    lblAirspeedValue.setFont(new Font("Tahoma", Font.BOLD, 22));
	    lblAirspeedValue.setBounds(20, 260, 160, 30);
	    panel.add(lblAirspeedValue);
	    
	    lblGroundSpeedValue = new JLabel("--");
	    lblGroundSpeedValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblGroundSpeedValue.setForeground(new Color(128, 0, 128));
	    lblGroundSpeedValue.setFont(new Font("Tahoma", Font.BOLD, 22));
	    lblGroundSpeedValue.setBounds(20, 340, 160, 30);
	    panel.add(lblGroundSpeedValue);
	    
	    lblAltitudeValue = new JLabel("--");
	    lblAltitudeValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblAltitudeValue.setForeground(new Color(0, 128, 0));
	    lblAltitudeValue.setFont(new Font("Tahoma", Font.BOLD, 22));
	    lblAltitudeValue.setBounds(20, 420, 160, 30);
	    panel.add(lblAltitudeValue);
	    
	    lblArmedValue = new JLabel("--");
	    lblArmedValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblArmedValue.setForeground(new Color(139, 0, 0));
	    lblArmedValue.setFont(new Font("Tahoma", Font.BOLD, 22));
	    lblArmedValue.setBounds(20, 500, 160, 30);
	    panel.add(lblArmedValue);
	    
	    lblDetectedValue = new JLabel("");
	    lblDetectedValue.setFont(new Font("Tahoma", Font.BOLD, 18));
	    lblDetectedValue.setForeground(new Color(255, 0, 0));
	    lblDetectedValue.setHorizontalAlignment(SwingConstants.CENTER);
	    lblDetectedValue.setBounds(0, 540, 200, 30);
	    panel.add(lblDetectedValue);
		
		// cleanup just before application window is closed.
	    frmGroundControlStation.addWindowListener(new WindowAdapter() {
	      @Override
	      public void windowClosing(WindowEvent windowEvent) {
	        super.windowClosing(windowEvent);
	        try {
	        	// map cleanup
	        	map.dispose();
	        	// close XBee connection
	        	xbeeRadio.close();
	        } catch (Exception exp) {
	        	exp.printStackTrace();
	        }
	      }
	    });
	    
	    // Timer for recurring stuff
	    timer.schedule(new TimerTask() {
			@Override
			public void run() {
				// send alive broadcast that includes system date-time for UAVs to sync their date-time with
	            GCSBroadcastSysTime();
			}
	    }, 2000, 10000);
	}
	
	private void GCSBroadcastSysTime() {
		GCSSendMessage(broadcastMAC, new String("SYS_TIME " + System.currentTimeMillis()));
	}
	
	private void GCSSendMessage(String destMAC, String msg) {
		String gcsMessage = "GCS > " + msg; 
		try {
			System.out.println(gcsMessage);
			System.out.println();
			synchronized(this) {
				xbeeRadio.sendDataAsync(new XBee64BitAddress(destMAC), gcsMessage.getBytes());
			}
		} catch (XBeeException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void dataReceived(XBeeMessage xbeeMessage) {
		int xbeeMaxPayload = xbeeMTU - 3;
		byte[] data = xbeeMessage.getData();
		int fragmentNumber = Util.bytesToShort(data, 0);
		int bufferLowerBound = (fragmentNumber - 1) * xbeeMaxPayload;
		int bufferUpperBound = bufferLowerBound + data.length - 3;
		//System.out.println("got data" + new String(xbeeMessage.getData()));
		//System.out.println("frameNumber: " + fragmentNumber + ", l: " + bufferLowerBound + ", u: " + bufferUpperBound);
		for (int i = bufferLowerBound, j = 3; i < bufferUpperBound; i++, j++) {
			xbeePacketBuffer[i] = data[j];
		}
		if (data[2] == 1) {
			//System.out.println("received last fragment, fragmentNumber: " + fragmentNumber);
			byte[] msgData = Arrays.copyOf(xbeePacketBuffer, bufferUpperBound);
			Util.resetBuffer(xbeePacketBuffer);
			xbeeMessageReceived(xbeeMessage, msgData);
		}
	}
	
	public void xbeeMessageReceived(XBeeMessage xbeeMessage, byte[] messageData) {
		String address = xbeeMessage.getDevice().get64BitAddress().toString();
		String message = null;
		try {
			message = Util.ungzip(messageData);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
        int rssiLocal = 0;
		try {
			rssiLocal = ByteUtils.byteArrayToInt(xbeeRadio.getParameter("DB"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println(address + "> " + messageData.length + " bytes of data (Local RSSI: -" + rssiLocal + "dBm)");
		
        try {
        	JSONObject jsonMsg = new JSONObject(message);
        	System.out.println("heartbeats: " + jsonMsg.getInt("heartbeats") + ", lastRSSI: " + jsonMsg.getInt("lastRSSI"));
        	System.out.println("lat: " + jsonMsg.getDouble("lat") + ", lon: " + jsonMsg.getDouble("lon") + ", alt: " + jsonMsg.getDouble("alt") + ", heading: " + jsonMsg.getInt("heading") + ", airspeed: " + jsonMsg.getDouble("airspeed") + ", groundspeed: " + jsonMsg.getDouble("groundspeed") + ", armed: " + jsonMsg.getBoolean("armed"));
        	if (jsonMsg.has("thermalImage")) {
        		// create thermal cam image from received data
            	updateThermalImage(jsonMsg.getString("thermalImage"));
        	}
        	
        	// Re-center map if not done already
            if (!this.mapRecentered) {
            	this.mapRecentered = true;
            	map.zoomTo(jsonMsg.getDouble("lat"), jsonMsg.getDouble("lon"), 18);
            }
            
            // change drone position and heading
            ((PictureMarkerSymbol)uavSymbol).setAngle(jsonMsg.getInt("heading"));
            //int uavNewSize = Math.round((float)((jsonMsg.getDouble("alt")/40.0) * 60.0)) + 16;
            //if (uavNewSize > 76) uavNewSize = 76;
            //((PictureMarkerSymbol)uavSymbol).setSize(uavNewSize, uavNewSize);
            Point uavNewPos = (Point)GeometryEngine.project(new Point(jsonMsg.getDouble("lon"), jsonMsg.getDouble("lat")), wgs84, map.getSpatialReference());
            graphicsLayer.movePointGraphic(uav1SymbolId, uavNewPos);
            graphicsLayer.updateGraphic(uav1SymbolId, uavSymbol);
            
            // update UAV info
            lblHeartbeatsValue.setText(jsonMsg.getInt("heartbeats") + "");
            lblAirspeedValue.setText(jsonMsg.getDouble("airspeed") + " mph");
            lblGroundSpeedValue.setText(jsonMsg.getDouble("groundspeed") + " mph");
            lblAltitudeValue.setText(jsonMsg.getDouble("alt") + " m");
            if (jsonMsg.getBoolean("armed")) {
            	lblArmedValue.setText("Armed");
            } else {
            	lblArmedValue.setText("Disarmed");
            }
            if (jsonMsg.has("detection")) {
            	lblDetectedValue.setText("PEOPLE DETECTED");
            } else {
            	lblDetectedValue.setText("");
            }
		} catch (JSONException e) {
			e.printStackTrace();
		}
        System.out.println();
	}
	
	private void updateThermalImage(String encodedThermalImageData) {
		//BufferedImage thermalImage = Util.generateThermalImage(encodedThermalImageData, thermalImagePalette);
		byte[] bs = Base64.getDecoder().decode(encodedThermalImageData);
		InputStream in = new ByteArrayInputStream(bs);
		BufferedImage thermalImage;
		try {
			thermalImage = ImageIO.read(in);
			AffineTransform tx = new AffineTransform();
			tx.scale(2.5, 2.5);
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BICUBIC);
			thermalImage = op.filter(thermalImage, null);
			imgFrame.setIcon(new ImageIcon(thermalImage));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Symbol makeUAVSymbol() {
		PictureMarkerSymbol symbol = null;
		BufferedImage image = null;

		File uavImageFile = new File(IMAGE_QUADCOPTER);
		try {
			image = ImageIO.read(uavImageFile);
			symbol = new PictureMarkerSymbol(image);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("unable to create picture marker symbol");
			return new SimpleMarkerSymbol(Color.YELLOW, 12, Style.CIRCLE);
		}
		return symbol;
	}
}
