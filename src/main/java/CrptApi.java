import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3";
    private final int requestLimit;
    private final long requestInterval;
    private long lastRequestTimestamp;
    private int requestCount;
    private final ReentrantLock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        } else {
            this.requestLimit = requestLimit;
        }
        this.requestInterval = timeUnit.toMillis(1);
        this.lastRequestTimestamp = System.currentTimeMillis();
        this.requestCount = 0;
    }

    public void createDocument(Document document, String signature) throws IOException {
        updateRequestCount();
        HttpPost request = new HttpPost(apiUrl + "/createDocument");
        request.setHeader("Content-Type", "application/json");
        String stringbody = preparePayload(document, signature);

        request.setEntity(new StringEntity(stringbody));
        try {
            httpClient.execute(request);
        } finally {
            request.releaseConnection();
        }
    }

    private String preparePayload(Document document, String signature) throws IOException {
        Body body = new Body(new String(Base64.getEncoder().encode(ConverterJson(document).getBytes())), signature);
        String bodyJson = String.valueOf(ConverterJson(body).getBytes());

        return bodyJson;
    }

    private void updateRequestCount() {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastRequestTimestamp > requestInterval) {
                requestCount = 0;
                lastRequestTimestamp = currentTime;
            }
            if (requestCount >= requestLimit) {
                try {
                    while (requestCount >= requestLimit) {
                        Thread.sleep(100);
                        currentTime = System.currentTimeMillis();
                        if (currentTime - lastRequestTimestamp > requestInterval) {
                            requestCount = 0;
                            lastRequestTimestamp = currentTime;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            requestCount++;
        } finally {
            lock.unlock();
        }
    }

    private String ConverterJson(Object body) {
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(body);
            return json;
        } catch (JsonProcessingException e) {

        }
        return json != null ? json : "";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class Document {
        private String description;
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private String importRequest;
        private String ownerInn;
        private String participant_inn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private String regDate;
        private String regNumber;
        private List<Product> products;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class Product {
        String certificate_document;
        String certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        String production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class Body {
        private String product_document;
        private String signature;
    }
}
