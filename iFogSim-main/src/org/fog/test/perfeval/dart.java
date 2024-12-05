package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

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

public class dart {

    private static final int BUOY_COUNT = 10; // Number of buoys
    private static final int SIMULATION_DURATION = 180; // Duration in milliseconds
    private static final double PRESSURE_THRESHOLD = 1070.0; // Threshold in hPa
    private static final double TEMPERATURE_THRESHOLD = 40.0; // Threshold in Celsius
    private static final int DATA_SAMPLING_INTERVAL = 1000; // Sampling every second in milliseconds
    private static final int TOTAL_READINGS = 10; // Total readings 

    private static Random random = new Random();

    private static long totalLatency = 0; // Track total latency
    private static int totalReadings = 0; // Count total sensor readings

    public static List<FogDevice> createDartBuoys(int count) {
        List<FogDevice> buoys = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            FogDevice buoy = createFogDevice(
                    "DARTBuoy_" + i,
                    1000, 2048, 10000, 10000, 1,
                    0.01, 100.0, 10.0);
            buoys.add(buoy);
        }
        return buoys;
    }

    private static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) {
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
            return new FogDevice(name, characteristics, new AppModuleAllocationPolicy(List.of(host)),
                    new LinkedList<>(), 10, ratePerMips, upBw, downBw, 0.5);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void simulateUnstableNetwork(FogDevice device) {
        // Simulate fluctuating bandwidth
        long upBw = 8000 + random.nextInt(4000); // Random uplink bandwidth: 8000-12000 Mbps
        long downBw = 8000 + random.nextInt(4000); // Random downlink bandwidth: 8000-12000 Mbps
        device.setUplinkBandwidth(upBw);
        device.setDownlinkBandwidth(downBw);

        // Log updated bandwidth
        System.out.println("Updated Network Bandwidth for " + device.getName() + ":");
        System.out.println("  Uplink Bandwidth: " + upBw + " Mbps");
        System.out.println("  Downlink Bandwidth: " + downBw + " Mbps");
    }

    private static void simulateDataTransmissionWithLatency() {
        // Random latency to simulate unstable network conditions
        long latency = 5000 + random.nextInt(3000); // Random latency: 5000-8000 ms
        System.out.println("Simulating data transmission with latency: " + latency + " ms");

        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void simulateAlertTransmissionWithLatency() {
        // Random latency to simulate unstable network conditions
        long latency = 3000 + random.nextInt(3000); // Random latency: 3000-6000 ms
        System.out.println("Simulating alert transmission with latency: " + latency + " ms");

        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void simulateDartSystem(List<FogDevice> buoys) {
        System.out.println("Starting DART Tsunami Simulation with Unstable Network...");
        long simulationStart = System.currentTimeMillis();
        int readingsTaken = 0;

        // Loop to take readings, stop after taking TOTAL_READINGS (10 in this case)
        while ((System.currentTimeMillis() - simulationStart) < SIMULATION_DURATION * 1000 && readingsTaken < TOTAL_READINGS) {
            for (FogDevice buoy : buoys) {
                if (readingsTaken >= TOTAL_READINGS) break; // Stop all readings are taken

                long startTime = System.currentTimeMillis(); // Start latency measurement

                // Simulate unstable network for the buoy
                simulateUnstableNetwork(buoy);

                double pressure = generateRandomPressure();
                double temperature = generateRandomTemperature();

                System.out.println("Data from " + buoy.getName() + ":");
                System.out.println("  Pressure: " + pressure + " hPa");
                System.out.println("  Temperature: " + temperature + " °C");

                simulateDataTransmissionWithLatency();

                if (pressure > PRESSURE_THRESHOLD && temperature > TEMPERATURE_THRESHOLD) {
                    System.out.println("  Threshold exceeded! Sending alert...");
                    simulateAlertTransmissionWithLatency();
                }

                long endTime = System.currentTimeMillis(); // End latency measurement
                long latency = endTime - startTime;
                totalLatency += latency; // Add to total latency
                totalReadings++; // Increment total readings

                System.out.println("  Latency for this reading: " + latency + " ms");
                System.out.println("---------------------------------");

                readingsTaken++;

                try {
                    Thread.sleep(DATA_SAMPLING_INTERVAL); // Simulate 1-second interval
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Simulation complete, calculate throughput based on latency
        System.out.println("Simulation complete.");
        System.out.println("Total readings: " + totalReadings);
        System.out.println("Total latency: " + totalLatency + " ms");

        // Calculate average latency
        double averageLatency = totalReadings == 0 ? 0 : (double) totalLatency / totalReadings;
        System.out.println("Average latency per reading: " + averageLatency + " ms");

        // Calculate throughput based on the inverse of average latency
        double throughput = averageLatency == 0 ? 0 : (double) totalReadings / (averageLatency / 1000); // Readings per second
        System.out.println("Throughput: " + throughput + " readings/second");
    }

    private static double generateRandomPressure() {
        return 1000 + random.nextDouble() * 100; // Range: 1000-1100 hPa
    }

    private static double generateRandomTemperature() {
        return random.nextDouble() * 35; // Range: 0-35 °C
    }

    public static void main(String[] args) {
        CloudSim.init(1, null, false);

        List<FogDevice> buoys = createDartBuoys(BUOY_COUNT);
        simulateDartSystem(buoys);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();
    }
}
