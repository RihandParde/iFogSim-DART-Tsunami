package org.fog.test.perfeval;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TsunamiWarningFunction implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String SNS_TOPIC_ARN = "arn:aws:sns:us-east-1:545009849675:TsunamiWarningTopic";

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();

        Map<String, Object> response = new HashMap<>();
        try {
            // Logging the input for debugging
            context.getLogger().log("Received input: " + event);

            // Extract fields from the JSON event
            double pressure = convertToDouble(event.getOrDefault("pressure", -1));
            double temperature = convertToDouble(event.getOrDefault("temperature", -1));
            String message = (String) event.getOrDefault("message", "Warning: Threshold Exceeded!");

            // Creating SNS message
            String snsMessage = String.format(
                "Alert: %s\nPressure: %.2f hPa\nTemperature: %.2f °C",
                message, pressure, temperature
            );

            // Logging of the time the message is being sent
            Instant snsSendTime = Instant.now(); // Current timestamp
            context.getLogger().log("Sending SNS message at: " + snsSendTime);

            // Publishing the message to SNS
            PublishRequest publishRequest = new PublishRequest(SNS_TOPIC_ARN, snsMessage);
            snsClient.publish(publishRequest);

            // Preparing response with SNS send time
            response.put("status", "SNS message sent");
            response.put("snsSendTime", snsSendTime.toString()); 
        } catch (Exception e) {
            context.getLogger().log("Error processing input: " + e.getMessage());
            response.put("status", "Error");
            response.put("errorMessage", e.getMessage());
        }

        return response;
    }

    private double convertToDouble(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        } else if (value instanceof Double) {
            return (Double) value;
        } else {
            throw new IllegalArgumentException("Unsupported type for conversion to double: " + value);
        }
    }
}
