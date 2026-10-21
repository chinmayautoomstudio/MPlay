import { assertEquals } from "jsr:@std/assert@1";
import { clientIp, detectRegion, isPublicIp, lookupCountry, parseRegion } from "./geo.ts";

Deno.test("Public IPv4 addresses are recognised", () => {
  assertEquals(isPublicIp("8.8.8.8"), true);
  assertEquals(isPublicIp("1.1.1.1"), true);
  assertEquals(isPublicIp("127.0.0.1"), false);
  assertEquals(isPublicIp("10.0.0.2"), false);
  assertEquals(isPublicIp("192.168.1.1"), false);
  assertEquals(isPublicIp("172.16.0.1"), false);
  assertEquals(isPublicIp("169.254.1.1"), false);
  assertEquals(isPublicIp("100.64.0.1"), false);
  assertEquals(isPublicIp("::1"), false);
  assertEquals(isPublicIp("2001:4860:4860::8888"), true);
});

Deno.test("The client IP is the first public address in X-Forwarded-For", () => {
  assertEquals(clientIp(new Headers({ "x-forwarded-for": "10.0.0.1, 8.8.8.8" })), "8.8.8.8");
  assertEquals(clientIp(new Headers({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" })), "1.2.3.4");
  assertEquals(clientIp(new Headers({ "x-real-ip": "9.9.9.9" })), "9.9.9.9");
  assertEquals(clientIp(new Headers({ "x-forwarded-for": "192.168.0.1" })), null);
  assertEquals(clientIp(new Headers()), null);
});

Deno.test("A missing or private IP is India", async () => {
  assertEquals(await detectRegion(new Headers()), "in");
  assertEquals(await detectRegion(new Headers({ "x-forwarded-for": "10.0.0.1" })), "in");
});

Deno.test("Country lookup maps IN to India and anything else to International", async () => {
  const india = async () => new Response(JSON.stringify({ success: true, country_code: "IN" }));
  const us = async () => new Response(JSON.stringify({ success: true, country_code: "US" }));
  const fail = async () => {
    throw new Error("offline");
  };
  assertEquals(await lookupCountry("1.2.3.4", india), "IN");
  assertEquals(await detectRegion(new Headers({ "x-real-ip": "8.8.4.4" }), us), "intl");
  assertEquals(await detectRegion(new Headers({ "x-real-ip": "8.8.8.8" }), fail), "in");
});

Deno.test("parseRegion accepts in and intl", () => {
  assertEquals(parseRegion(undefined), null);
  assertEquals(parseRegion("in"), "in");
  assertEquals(parseRegion("intl"), "intl");
  assertEquals(parseRegion("us"), { error: "invalid_region" });
});
