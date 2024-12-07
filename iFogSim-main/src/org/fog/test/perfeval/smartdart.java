package org.fog.test.perfeval;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.GetItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.regions.Regions;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Random;

public class smartdart {
    private static final String DYNAMODB_TABLE = "SensorThresholds"; // Define DynamoDB table
    private static final String LAMBDA_FUNCTION = "TsunamiWarningFunction"; //Define Lambda function
    private static DynamoDB dynamoDB;
    private static AWSLambda lambdaClient;

    private static FileWriter metricFile;

    // Metrics variables
    private static long totalReadings = 0;
    private static long totalLatency = 0;
    private static double totalThroughput = 0;

    private static final Random random = new Random();

    // Initialize AWS and Metrics Logging
    public static void initializeAWS() {
        AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new ProfileCredentialsProvider("default"))
                .build();
        dynamoDB = new DynamoDB(dynamoDBClient);

        lambdaClient = AWSLambdaClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new ProfileCredentialsProvider("default"))
                .build();

        try {
            metricFile = new FileWriter("metrics.csv");
            metricFile.write("Time,Device,Latency (ms),Throughput (readings/sec),CPU Utilization (%),Memory Utilization (%)\n");
        } catch (IOException e) {
            System.err.println("Failed to initialize metrics file: " + e.getMessage());
        }
    }

    // Create multiple DART Buoy Fog Devices
    public static List<FogDevice> createMultipleSmartDartBuoys(int count) {
        List<FogDevice> smartDartBuoys = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            FogDevice smartDartBuoy = createSmartDartBuoy("SMARTDARTBuoy_" + i, 
                                                1000, 2048, 10000, 10000, 1, 
                                                0.01, 100.0, 10.0);
            smartDartBuoys.add(smartDartBuoy);
        }
        return smartDartBuoys;
    }

    // Create a single DART Buoy FogDevice
    private static FogDevice createSmartDartBuoy(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        PowerHost host = new PowerHost(
                FogUtils.generateEntityId(),
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000),
                1000000,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Linux", "x86", host, 10.0, 3.0, 1.0, 0.05, 0.001);

        try {
            return new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(List.of(host)), new LinkedList<>(), 10, ratePerMips, upBw, downBw, 0.5);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Simulate minor fluctuating network latency
    private static long simulateNetworkLatency() {
        return 50 + random.nextInt(300); // Latency between 50ms and 350ms
    }

    // Simulate minor fluctuating bandwidth
    private static double simulateNetworkBandwidth() {
        return 1000 + random.nextGaussian() * 200; // Average bandwidth 1000 Kbps with fluctuations
    }

    // Simulate Sensor Readings and Collect Metrics
    private static void simulateMultipleSensors(List<FogDevice> smartDartBuoys) {
        System.out.println("Starting Simulation for SMARTDART Tsunami Warning System...");

        for (FogDevice smartDartBuoy : smartDartBuoys) {
            for (int i = 0; i < 1; i++) {
                long totalStartTime = System.currentTimeMillis(); // Capture total start time

                // Simulate network delay
                long networkLatency = simulateNetworkLatency();
                try {
                    Thread.sleep(networkLatency);
                } catch (InterruptedException e) {
                    System.err.println("Simulation interrupted during network delay: " + e.getMessage());
                }

                // Generate random sensor readings
                double pressure = generateRandomPressure();
                double temperature = generateRandomTemperature();

                System.out.println("Sensor Readings from " + smartDartBuoy.getName() + ":");
                System.out.println("Pressure: " + pressure + " hPa");
                System.out.println("Temperature: " + temperature + " °C");

                // Fetch thresholds from DynamoDB
                Double pressureThreshold = getThresholdValue("pressure", "High");
                Double temperatureThreshold = getThresholdValue("temperature", "High");

                // Trigger Lambda function if both thresholds exceed
                boolean isThresholdExceeded = false;
                if (pressureThreshold != null && pressure > pressureThreshold 
                        && temperatureThreshold != null && temperature > temperatureThreshold) {
                    isThresholdExceeded = true;
                    System.out.println("Both pressure and temperature thresholds exceeded!");
                }

                if (isThresholdExceeded) {
                    JSONObject payload = new JSONObject()
                            .put("pressure", pressure)
                            .put("temperature", temperature)
                            .put("message", "Tsunami Warning: Both thresholds exceeded!");

                    try {
                        // Simulate bandwidth fluctuation by throttling Lambda function invocation
                        double networkBandwidth = simulateNetworkBandwidth();
                        Thread.sleep((long) (1024 / networkBandwidth * 1000)); // Adjust delay based on bandwidth

                        // Invoke Lambda function
                        InvokeRequest invokeRequest = new InvokeRequest()
                                .withFunctionName(LAMBDA_FUNCTION)
                                .withPayload(payload.toString());
                        lambdaClient.invoke(invokeRequest);
                    } catch (Exception e) {
                        System.err.println("Failed to invoke Lambda: " + e.getMessage());
                    }
                } else {
                    System.out.println("Readings are within safe thresholds.");
                }

                // Calculate total latency
                long totalEndTime = System.currentTimeMillis(); // End time after SNS message
                long totalLatencyCurrent = totalEndTime - totalStartTime;

                // Update total metrics
                totalReadings++;
                totalLatency += totalLatencyCurrent;

                // Calculate throughput based on time and readings
                double throughput = (totalReadings > 0) ? (totalReadings / (totalLatency / 1000.0)) : 0;
                totalThroughput = throughput; // Update total throughput

                // Log total latency
                System.out.println("Total Latency (Simulation to SNS): " + totalLatencyCurrent + " ms");
                System.out.println("-----------------------------");

                // Collect metrics for CSV
                double totalAllocatedMips = smartDartBuoy.getHost().getPeList().stream()
                        .mapToDouble(pe -> pe.getPeProvisioner().getTotalAllocatedMips()).sum();
                double totalMips = smartDartBuoy.getHost().getTotalMips();
                double cpuUtilization = (totalMips > 0) ? (totalAllocatedMips / totalMips) * 100.0 : 0.0;
                double memoryUtilization = ((smartDartBuoy.getHost().getRam() - smartDartBuoy.getHost().getRamProvisioner().getAvailableRam())
                        * 100.0) / smartDartBuoy.getHost().getRam();

                logMetrics(System.currentTimeMillis(), smartDartBuoy.getName(), totalLatencyCurrent, throughput, cpuUtilization, memoryUtilization);

                try {
                    // Short bursts of 15 seconds
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    System.err.println("Simulation interrupted: " + e.getMessage());
                }
            }
        }
    }

    // Log Metrics to File
    private static void logMetrics(long timestamp, String device, long totalLatency, double throughput, double cpuUtilization, double memoryUtilization) {
        try {
            metricFile.write(String.format("%d,%s,%d,%.2f,%.2f,%.2f\n",
                    timestamp, device, totalLatency, throughput, cpuUtilization, memoryUtilization));
        } catch (IOException e) {
            System.err.println("Failed to log metrics: " + e.getMessage());
        }
    }

    // Random Sensor Readings
    private static double generateRandomPressure() {
        return 1000 + Math.random() * 100;
    }

    private static double generateRandomTemperature() {
        return Math.random() * 35;
    }

    // Fetch Threshold from DynamoDB
    public static Double getThresholdValue(String sensorType, String thresholdLevel) {
        try {
            Table table = dynamoDB.getTable(DYNAMODB_TABLE);
            GetItemOutcome outcome = table.getItemOutcome(new GetItemSpec().withPrimaryKey("sensorType", sensorType, "thresholdLevel", thresholdLevel));
            if (outcome.getItem() != null) {
                return outcome.getItem().getDouble(sensorType + "Threshold");
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch threshold: " + e.getMessage());
        }
        return null;
    }
    
    // Close Metrics Logging
    public static void closeMetrics() {
        try {
            if (metricFile != null) metricFile.close();
        } catch (IOException e) {
            System.err.println("Failed to close metrics file: " + e.getMessage());
        }

        // Output the total metrics
        System.out.println("Simulation Complete");
        System.out.println("Total Readings: " + totalReadings);
        System.out.println("Total Latency: " + totalLatency + " ms");
        System.out.println("Average Latency: " + (totalReadings > 0 ? totalLatency / totalReadings : 0) + " ms");
        System.out.println("Throughput: " + totalThroughput + " readings/sec");
    }

    public static void main(String[] args) {
        CloudSim.init(1, null, false);
        initializeAWS();

        List<FogDevice> smartDartBuoys = createMultipleSmartDartBuoys(10); // Simulate 10 buoys
        simulateMultipleSensors(smartDartBuoys);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        closeMetrics(); // Close metrics file
    }
}
