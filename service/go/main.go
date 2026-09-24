package main

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

var config = struct {
	AmqpURL    string
	Queue      string
	FleetURL   string
	PartnerURL string
}{
	AmqpURL:    envOr("AMQP_URL", "amqp://guest:guest@localhost:5672/"),
	Queue:      envOr("QUEUE", "device-status"),
	FleetURL:   envOr("FLEET_URL", "http://localhost:4001"),
	PartnerURL: envOr("PARTNER_URL", "http://localhost:4002"),
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

type StatusChange struct {
	Serial          string   `json:"serial"`
	Status          string   `json:"status"`
	LimitingFactors []string `json:"limitingFactors"`
	ObservedAt      string   `json:"observedAt"`
}

func handleStatusChange(d amqp.Delivery) error {
	var change StatusChange
	if err := json.Unmarshal(d.Body, &change); err != nil {
		return err
	}

	log.Printf("change=%s serial=%s status=%s factors=%v redelivered=%v",
		d.MessageId, change.Serial, change.Status, change.LimitingFactors, d.Redelivered)

	// TODO: the steps in the README go here.

	return nil
}

func main() {
	conn, ch := connect()
	defer conn.Close()

	if err := ch.Qos(1, 0, false); err != nil {
		log.Fatalf("qos: %v", err)
	}

	deliveries, err := ch.Consume(config.Queue, "", false, false, false, false, nil)
	if err != nil {
		log.Fatalf("consume %s: %v", config.Queue, err)
	}

	log.Printf("consuming %s", config.Queue)

	for d := range deliveries {
		if err := handleStatusChange(d); err != nil {
			log.Printf("message %s failed: %v", d.MessageId, err)
			d.Nack(false, false)
			continue
		}
		d.Ack(false)
	}
}

// --- plumbing, nothing below here is part of the exercise ---

func connect() (*amqp.Connection, *amqp.Channel) {
	for attempt := 1; ; attempt++ {
		conn, err := amqp.Dial(config.AmqpURL)
		if err == nil {
			ch, chErr := conn.Channel()
			if chErr == nil {
				return conn, ch
			}
			conn.Close()
			err = chErr
		}
		if attempt >= 30 {
			log.Fatalf("could not reach the broker at %s: %v", config.AmqpURL, err)
		}
		time.Sleep(time.Second)
	}
}

const defaultTimeout = 3 * time.Second

var httpClient = &http.Client{}

type Response struct {
	Status int
	Body   map[string]any
}

func Get(url string, timeout ...time.Duration) (Response, error) {
	return call("GET", url, nil, timeout...)
}

func Put(url string, body any, timeout ...time.Duration) (Response, error) {
	return call("PUT", url, body, timeout...)
}

func Post(url string, body any, timeout ...time.Duration) (Response, error) {
	return call("POST", url, body, timeout...)
}

func call(method, url string, body any, timeout ...time.Duration) (Response, error) {
	deadline := defaultTimeout
	if len(timeout) > 0 {
		deadline = timeout[0]
	}

	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			return Response{}, err
		}
		reader = bytes.NewReader(encoded)
	}

	req, err := http.NewRequest(method, url, reader)
	if err != nil {
		return Response{}, err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	ctx, cancel := context.WithTimeout(req.Context(), deadline)
	defer cancel()

	res, err := httpClient.Do(req.WithContext(ctx))
	if err != nil {
		return Response{}, err
	}
	defer res.Body.Close()

	raw, err := io.ReadAll(res.Body)
	if err != nil {
		return Response{}, err
	}

	out := Response{Status: res.StatusCode}
	if len(raw) > 0 {
		json.Unmarshal(raw, &out.Body)
	}
	return out, nil
}
