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
)

var config = struct {
	Port       string
	FleetURL   string
	PartnerURL string
	BusURL     string
}{
	Port:       envOr("PORT", "3000"),
	FleetURL:   envOr("FLEET_URL", "http://localhost:4001"),
	PartnerURL: envOr("PARTNER_URL", "http://localhost:4002"),
	BusURL:     envOr("BUS_URL", "http://localhost:4003"),
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

type StatusChange struct {
	Status          string   `json:"status"`
	LimitingFactors []string `json:"limitingFactors"`
	ObservedAt      string   `json:"observedAt"`
}

func handleStatusChange(w http.ResponseWriter, r *http.Request) {
	serial := r.PathValue("serial")
	changeID := r.Header.Get("X-Change-Id")

	var change StatusChange
	if err := json.NewDecoder(r.Body).Decode(&change); err != nil {
		sendJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return
	}

	log.Printf("change=%s serial=%s status=%s factors=%v", changeID, serial, change.Status, change.LimitingFactors)

	// TODO: the three steps in the README go here.

	sendJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func main() {
	mux := http.NewServeMux()

	mux.HandleFunc("POST /v1/devices/{serial}/status", handleStatusChange)

	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		sendJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	log.Printf("listening on :%s", config.Port)
	log.Fatal(http.ListenAndServe(":"+config.Port, mux))
}

// --- plumbing, nothing below here is part of the exercise ---

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

func sendJSON(w http.ResponseWriter, code int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(body)
}
