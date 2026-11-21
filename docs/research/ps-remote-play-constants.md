# PlayStation Remote Play: interoperability constants

Companion to [ps-remote-play-protocol.md](ps-remote-play-protocol.md). Untracked until the owner approves.

**Legal status (owner decision required).** Every table below was extracted from Sony's official Remote Play client
by third parties (published in the AGPL-licensed Chiaki/chiaki-ng projects, read at commit `43728af`, 2026-11-03).
They are interoperability data, not code; whether DELOS may ship them is a legal question for the owner (EU Directive
2009/24/EC Art. 6 permits reverse engineering for interoperability; this note is not legal advice).
Data was copied by a script as raw bytes; no source text was copied. Only bytes the algorithms actually read are listed
(the 0x70-byte rows of KEYS_A/KEYS_B are reduced to their first 16 bytes).

Hex, row index on the left.

## ECHO_A

16 bytes. PS4 RP-Version 8.0/9.0 session key derivation (ambassador).

```
00: 01 49 87 9b 65 39 8b 39 4b 3a 8d 48 c3 0a ef 51
```

## ECHO_B

16 bytes. PS4 8.0/9.0 session key derivation (bright) and 8.0/9.0 registration aeropause.

```
00: e1 ec 9c 3a dd bd 08 85 fc 0e 1d 78 90 32 c0 04
```

## REGIST_AES_KEY_PS4_PRE10

16 bytes. Base registration AES key for PS4 RP-Version 8.0/9.0.

```
00: 3f 1c c4 b6 dc bb 3e cc 50 ba ed ef 97 34 c7 c9
```

## HMAC_KEY_PS5

16 bytes. HMAC-SHA256 key of the RP IV derivation, target PS5 RP-Version 1.0.

```
00: 46 46 87 b3 49 ca 8c e8 59 c5 27 0f 5d 7a 69 d6
```

## HMAC_KEY_PS4

16 bytes. HMAC key, PS4 RP-Version 10.0 (and any target not listed otherwise).

```
00: 20 d6 6f 59 04 ea 7c 14 e5 57 ff c5 2e 48 8a c8
```

## HMAC_KEY_PS4_PRE10

16 bytes. HMAC key, PS4 RP-Version 8.0/9.0.

```
00: ac 07 88 83 c8 3a 1f e8 11 46 3a f3 9e e3 e3 77
```

## KEYS_A_PS4

32 rows x 16 bytes. Row = nonce[0] >> 3. PS4 10.0 session ambassador.

```
00: df 40 82 a6 2e d8 6b 2b f5 6c 5f f5 fb 21 69 b4
01: 0d c0 ac 7e 04 b5 f0 75 de 5e ba fa a6 25 56 de
02: 75 58 21 fe 58 8f d0 75 f6 20 aa 54 3a 45 54 f3
03: 62 1c 56 f1 86 88 db ce 10 bf 3d de e6 f5 02 51
04: 1a 62 eb 47 92 0c a7 e0 6c 33 f5 84 ec 69 31 db
05: ed 98 d8 4f 23 3f 77 29 ac a5 bc d0 8f 2b 89 b9
06: 65 ee be d9 16 ea a9 66 07 97 71 43 dd 64 e3 00
07: aa 10 b6 ce 73 e3 e3 8c f8 5d 0e 95 3e 1a 4f be
08: 77 6b 50 47 7c fc a4 d4 2c 4d 53 c1 26 26 92 38
09: 69 6c 1e 85 48 ea 33 5b b1 28 09 e3 24 47 7a 12
0a: c3 4d e8 83 78 f1 8c 6a b3 87 7f 67 df 47 c4 2f
0b: 1d ee a1 fd ed 82 19 1e 90 87 ab db 80 b7 b2 ab
0c: f4 b5 ae fc 53 0c ec 36 f0 b4 30 ea 9b de 60 31
0d: 81 f5 12 72 02 35 87 65 01 ea 4f f7 95 ba b1 09
0e: a2 ef 3a a3 0c 99 4e 6e d4 64 bb d2 a0 38 39 e8
0f: 36 bf 20 21 1b 34 5c bd 2c 64 c5 6f 7f b9 91 65
10: f3 d2 c5 5d 1d 89 78 09 bb 6a 3a 0a 9c 01 4c d9
11: 19 7f c2 38 94 8a e4 2d 7f ff f7 45 57 21 98 aa
12: 78 cf 63 53 57 08 11 55 65 1b 55 5e 8d ce 68 2d
13: 02 d3 2c ec f8 8f 14 62 7c 78 47 73 e8 d5 40 2d
14: 40 9f 56 54 de 96 65 61 76 06 b6 db 16 c8 1c 0b
15: 76 77 fd 84 7b b4 3c 87 01 09 89 4c 0f 8e 44 c0
16: 0b 0c 1f 20 aa 75 52 b5 bc a2 55 d6 06 3f f1 6a
17: 2b b2 e4 70 da eb 49 e4 1b 1f df 45 ee 5a 69 68
18: 3f 99 26 cb 6b ac ae 2b bc 75 f2 8d 64 ff d2 2b
19: b4 27 78 2b cd 16 10 81 b1 59 fe e4 1b 62 d1 cf
1a: 31 43 8f 85 7d 59 a8 d2 26 4c a0 fb f8 db 6a 12
1b: 93 e2 bc e7 26 df 77 88 e0 90 c0 2d 20 c9 8c fa
1c: ef 0f c6 3b 5b 4c f1 1b 35 92 b9 74 9e 0f 16 2a
1d: b3 7e d9 11 1a a3 41 c4 65 19 1d db 92 f4 3e f4
1e: e0 cd a6 ee 23 ef 64 87 65 5f fb 5f 6c 29 4a f9
1f: 80 a2 e0 69 bc 29 e3 48 08 a6 dc bc 68 d7 d7 84
```

## KEYS_A_PS5

32 rows x 16 bytes. Row = nonce[0] >> 3. PS5 session ambassador.

```
00: 7f ae 83 20 47 39 b6 5b 23 9b 5c a5 37 c4 df 59
01: 2a b3 fd b2 32 86 3c e7 1a 96 14 9d b1 d9 90 72
02: 88 ac e4 14 77 23 04 9e 30 ec 3f 27 e7 c2 b1 76
03: a1 ee 5d ea c0 b9 68 ee a2 61 b9 b4 69 12 09 90
04: ff 23 bb 18 93 56 ff 48 d8 d3 71 a1 c3 61 30 83
05: 54 05 6b 8d 42 56 04 01 d8 0b e7 83 0a 66 b2 94
06: 52 17 28 5c e5 10 c7 77 03 ba 51 89 73 6a 28 68
07: b1 67 78 79 4c 6e b1 97 b3 b7 7f 54 a7 02 55 35
08: e7 56 6b c3 3c ee 89 31 d5 f1 23 5c ec 45 3c 55
09: 33 17 20 a0 76 17 23 3f 76 ac b4 cc 07 5a 62 22
0a: 58 96 15 c9 f8 19 7b a0 07 b3 01 c8 fc e0 b6 f4
0b: 30 b6 68 87 50 06 98 54 51 2b 2e 57 15 01 1f 85
0c: ba fa 39 ec cf e8 12 88 30 83 a6 2a d2 08 d5 ae
0d: fb 2f 13 83 ce 0a 24 cb 42 7a a6 26 6d 52 d6 9b
0e: ab ca 48 53 9e 31 05 5d 50 78 e5 0d 2d 87 7c 83
0f: 3f bc 19 ae 90 1b ce 02 1d 75 d3 d1 6f d1 c2 f0
10: 48 f0 b3 37 05 4d be 69 27 da 04 c2 33 a3 45 b1
11: 71 71 e0 f3 cc 8b a6 18 88 6d 1e 2f 3c 6b 69 89
12: e6 c5 f9 00 ca 32 9f 20 87 d7 ea 15 18 bb e0 24
13: 45 0c e7 c2 1a bc cc ff 6d 3f 15 57 94 1c a2 25
14: 61 a2 b1 ef 31 a8 a9 02 1a a2 89 f8 ec ee 97 f6
15: 9d 95 f3 c7 5d cb 12 08 2c 49 e1 d5 93 67 52 a3
16: 9e e5 44 62 ee eb be a4 9a 95 3d b3 69 3f da 9a
17: f3 e2 0b 72 55 13 8b 97 12 58 f4 4a 52 36 c6 42
18: 6c 78 c9 8b 44 bc 85 80 93 ba 6c e3 d2 39 95 c6
19: 58 14 c5 a2 d9 ef 2d 9d 97 f7 ec 55 f7 2e 4c 0f
1a: e8 b8 34 86 45 fc f4 61 93 4a 7f 2c 3f eb d1 ce
1b: c3 d2 1c c1 0e 4a 44 f7 74 ce 00 69 43 99 bd b7
1c: 7d 2b 99 ce 22 3b 2d c9 6f 4d 38 87 45 20 c5 67
1d: 11 13 e6 e7 86 68 92 93 0a 71 a5 5b 95 01 ae 09
1e: 17 d5 d7 2f 9e 56 86 d1 98 9c 24 a2 82 84 ad ee
1f: 07 fa cf ea 1b e7 6d c1 f3 8a 0b 21 4e 66 47 a4
```

## KEYS_B_PS4

32 rows x 16 bytes. Row = nonce[7] >> 3. PS4 10.0 session bright.

```
00: 76 65 9d e4 f7 6c 45 1f 73 42 47 c3 92 32 f4 38
01: 9b ee b5 ad a3 56 85 c3 bd 9b ef ac 55 98 1f af
02: 15 16 fb c3 09 d3 ad a4 ea a3 4a be f2 88 9a 34
03: ba bd ac 73 7a e8 bc ad ba 5e a6 f0 b8 5a 51 2a
04: c1 88 7d ae f4 b8 89 b5 ce dd 27 be 6f 6c 81 8f
05: 21 9f 9c 5d 98 72 a3 2e 74 49 70 af 85 7b 14 66
06: bf e8 25 af c2 df f3 a1 76 f4 2b 8c 6f 01 ce 96
07: ba 43 f7 02 7a 0e 77 15 45 03 aa 84 88 56 b1 ee
08: 55 0f 6e c6 cd 6b c4 f0 a1 05 6b 3f c4 c9 47 95
09: e9 de 2e 30 3c d6 46 ca 50 25 f7 a2 66 ec a5 6e
0a: 41 8c b0 51 74 32 ea 96 ce 70 1c 5c 26 34 66 3a
0b: 11 1e d0 d8 99 37 ae 35 fa e4 0b 4c 52 0c 12 3a
0c: 1a b0 07 27 82 c5 13 86 3d b5 22 fb 7d 2a 4d 45
0d: f5 b3 24 4a 2d 69 46 51 65 9a 37 11 7d de 9c 7c
0e: 1e f8 70 88 7d f9 2c f4 80 5c 6f 55 73 f2 a0 90
0f: 52 7e 2c 50 36 d0 3b f7 1f d8 4a 06 11 f3 21 1f
10: 3a f7 df 1c a8 85 d5 f8 54 63 75 36 ea 30 5a 13
11: 2a 8e e9 da ad b2 52 94 74 69 70 19 c4 c8 36 f3
12: bd 0f 6f b9 c7 42 79 fd ac 8f 1c cc 01 9f 45 3a
13: 71 2e f1 d7 33 a2 ae 2d 89 6a cb 63 ed 8d 83 10
14: f5 87 f7 18 d6 7a 60 5c 2b 4a 19 0a ea 3f d1 5b
15: d5 6d ad 65 bd 52 d2 10 71 ca 4b e5 72 cd ae a7
16: 40 24 19 4c 51 19 52 cc c2 70 63 c6 34 1c 97 14
17: 74 ad 1e 30 89 9f 84 58 ff d5 84 41 a6 68 16 c8
18: 24 c8 8a c5 36 42 e3 d4 f7 af 74 ac 89 ee 47 2e
19: 1d 1d 7d 89 9c 21 d0 aa a9 01 ee b2 70 c7 c3 11
1a: 30 ef 8c f6 23 bf fb d0 bb 51 a9 f6 1d a7 d2 c7
1b: f9 c0 15 08 c7 10 67 d1 42 13 27 22 c3 e3 94 66
1c: d4 13 19 2a f2 9d 4e cc 2a b3 df 95 c5 b6 91 34
1d: dc 15 d4 80 5b d8 ec 2e 49 d7 d8 45 0e 7d ba 09
1e: d8 68 75 f3 f9 92 8e 78 be 43 b9 28 a9 2e de c9
1f: 79 7d 6d 5b 23 7e 5e 5d 53 cf 01 ea 86 5f 8a 4b
```

## KEYS_B_PS5

32 rows x 16 bytes. Row = nonce[7] >> 3. PS5 session bright.

```
00: e1 34 17 1d f5 17 da e1 1b e6 0c 73 35 d4 6b 42
01: f4 b6 93 72 ae 46 40 5d 26 30 98 f8 c7 be b7 2c
02: cf 79 70 90 c5 7a f3 b1 3d 59 08 59 e1 41 ae 65
03: 1d 37 b5 a7 8b 19 8a 50 50 2d a9 89 4e 6e 7d bf
04: 29 89 b6 11 32 db 0d 75 58 e2 9a 1a 1b 08 66 2a
05: 30 9b 97 be 9f 0c 1e 4e ff 3a e2 06 15 ba 57 9d
06: 49 42 db 52 4b bb d9 63 8f 6d 9d 3b d7 e3 49 bc
07: dc 76 ac 59 e6 e3 56 81 1d 29 f9 65 48 c3 70 50
08: d4 88 bf 3a 7e 00 22 b2 8a 80 fe f9 75 1a d1 cc
09: 19 07 60 62 dc 4b 99 64 95 ea f6 cc fc 97 e5 7f
0a: eb fc da f3 42 3a 43 b0 d2 24 fe 04 d1 31 47 77
0b: 55 bc 0a 75 90 82 a2 18 2f 12 26 31 92 eb 47 38
0c: 6e 45 e0 99 83 80 74 02 e3 04 e6 7a 64 c9 08 8a
0d: f0 99 34 26 a2 a5 c8 15 e9 91 6f d7 c1 c7 e6 c5
0e: 5d 2c 6b 55 bb 5b 44 d1 f8 36 9b 81 19 3a 47 18
0f: d7 ae f2 b8 b1 c1 b7 d6 e8 9f 6a 68 31 7d be f3
10: 9f f2 e2 37 41 09 86 52 3f ac a4 98 4e 42 6c 81
11: 64 31 6b fd f8 01 75 14 ac b5 18 2f ef d5 42 4d
12: 2d 39 2c 11 24 22 0f ac 65 bf d6 da ef 82 00 fb
13: 62 2c 8d d8 b6 77 6e 1a 2a a5 34 da ad 75 64 88
14: 6c b0 18 ce c8 ed 82 5d 4d 18 43 89 57 06 fd c2
15: 5c 82 23 2b 43 7f 55 ec f8 fb 23 ed 49 92 1d cf
16: 3b 6a ad 42 07 1a 76 5e da 5a 23 44 1d 2d c9 ec
17: 61 f2 9b 4f b4 12 33 4d 6a 86 e2 f0 d9 4d 45 7a
18: 09 8a 95 ed d9 86 2a f2 c6 fd 72 2f 3d 18 9f 7f
19: ab 50 48 85 71 0d 68 55 91 96 a2 87 51 da f8 4c
1a: b0 45 bd 9f 98 8d 5a 50 47 65 68 6f c3 08 78 4c
1b: 90 c2 56 a9 62 03 d1 bd 77 54 70 6a 96 ce bd e0
1c: 57 cb f4 4d 2c 8f b8 2c ae 4e 31 4d ee b9 50 31
1d: 75 30 ff b1 fc ef 2e 05 08 28 3d 44 8a 4a 03 e3
1e: 54 69 13 d5 9b 68 12 5b 4a cf a2 b9 50 9f c0 cc
1f: 1b cd 10 d7 92 29 65 1e 02 8d 08 4e 68 d3 70 f7
```

## KEYS_0_PS4

16 rows x 32 bytes. Registration bright byte i = row i, column key0_off (0..31). PS4 10.0.

```
00: be ce 5d f0 c1 7d b5 d0 cb 30 13 5d aa 56 23 fb c4 bc f1 8f 38 57 fb d4 d4 3f 26 38 b5 ce ed 6a
01: 21 bc 38 d0 1e 68 cc 7b 45 d1 be 42 1a 08 aa 16 fd b0 c0 f4 da 35 e9 12 fd 21 07 48 34 c1 fc 9f
02: 8c b6 cb 5d b2 9c 84 e0 1a fa a0 c7 eb 3a 93 b3 b3 f1 15 af 13 bd 21 ab ea 5b 80 50 6b 31 1d 7c
03: 1d 40 ba 3c 56 0e e7 94 3a 5b a1 40 80 74 0a ad 28 cf 47 df 42 a6 69 e9 5e bb c0 c0 0e b2 c5 8a
04: ee 08 03 d2 84 e5 91 00 1d 46 06 55 09 9d 39 9f d8 e7 fd ad 9e 93 97 c5 ea e7 a3 10 a7 f2 a2 93
05: 7f 07 04 b4 ee bb bf 88 23 9c 6e a7 62 b1 4b 67 1e b8 3b 1f 64 93 5a 99 ec da fd 0c 6a b7 fe e4
06: 12 76 32 65 b8 41 23 d1 17 09 9c 24 2d 5c 9d 12 79 de a1 ce 69 ac a4 bc 39 2f 57 38 84 61 2d 2a
07: e8 04 f8 d5 9d 0b ff 7e 56 0c ec 87 0a 1e ab df 93 81 13 ee cf 32 02 5a bf b0 17 b7 ba b5 7f f0
08: 01 7b e1 cb 39 7e 60 6d a4 75 6e 29 92 45 a6 4f 74 00 86 78 73 be fd 3e e0 d1 0c 6c 0b 49 09 83
09: 6c 85 8a 1d cb 16 ce 81 7c 49 c9 2c 63 61 de e2 3f 98 b2 73 f0 9a ec 7b 7c f1 c9 e1 7f a5 19 8b
0a: 4b e8 38 a4 34 7d f4 28 fe 0d 4d 11 57 0c 95 f1 af d7 34 80 f4 eb 9b 50 e6 6a 5d ea ce 0c 85 4e
0b: c5 5b 93 44 c4 24 98 80 fc f7 72 9c 31 0b ee 89 67 b3 a2 69 4f b3 79 5a 14 02 70 ed 50 13 75 00
0c: 6a f3 c6 05 1a 00 33 34 f5 ac 9e 04 db c2 00 b0 1b c4 f3 97 9d 7f be b8 23 8d 99 e7 cb 74 37 4c
0d: 57 ec d2 69 49 46 75 74 af 51 40 a4 11 7b b3 2f 51 da e2 ef 33 73 12 18 25 39 03 09 ca 49 dc 8e
0e: f1 94 d7 80 17 9e 87 46 c1 04 78 d1 e5 3d 25 88 ec 72 3a 28 41 68 14 6e 10 e4 c9 57 75 90 fe 22
0f: 1a 63 8e f4 b8 8d 1a 36 fd b6 cb 72 c2 97 52 9f 91 72 1b 75 57 90 3b fd 5a 93 8c db fc a3 03 df
```

## KEYS_0_PS5

Same for PS5.

```
00: 24 d8 c2 69 4c 67 78 71 ee 31 bd 2b 83 b2 1d 61 c9 a7 8e ed 9a d3 6a 6b 5c c8 35 79 a7 24 e2 17
01: 06 60 2e df f4 db 27 10 55 d9 ea 16 4e 90 0c bf 40 6f 54 a5 31 70 2d 5d 1e 27 df 37 40 ba 9d 5d
02: ff e1 05 70 80 d4 b7 c2 96 7f 2f 42 eb 5a 08 de c1 b5 52 15 f6 b5 f2 d9 69 a5 c7 c4 7f 46 64 a4
03: fd 46 98 a7 e1 2a 8e 6f af 65 42 28 b9 c2 6f 3e e3 e4 4e e4 5b 9d 60 10 b8 5a b0 7d 04 0c 4c 24
04: 78 bd b8 ba db 8f e3 a0 75 6d 28 c2 33 5b 32 83 dd 51 b0 a5 8d 09 66 e4 5c b8 70 0b e6 82 14 b6
05: d2 b0 c2 e0 55 f3 84 ad 9d 3a f8 77 f5 9d 9a a9 7d f1 45 1b 9b 55 25 d8 c1 ff 03 a5 48 0b 1b 19
06: 0c bd e0 cd 48 f3 2c 99 19 d6 b8 bb d6 35 43 6f 71 e3 ef 3e 97 b8 e9 40 a8 47 e0 e0 01 16 9d a7
07: e5 94 4b 1d d2 80 a2 7f f2 98 10 38 0d b8 56 c3 7a 4b 4c 85 ec 2f 23 89 af d5 ba 9a ad b0 61 9c
08: 51 b4 6d 02 49 26 a4 34 84 20 35 30 23 0a 47 14 32 1a 96 0e e8 0f 96 96 d4 ba 68 3a 67 15 74 e0
09: d6 60 4c 68 50 73 14 2f 11 59 ac c8 32 d1 db 4c 8a 94 75 33 61 d1 d4 fd aa 6a 61 68 d8 ae 31 4f
0a: b8 07 7b 27 0f f9 0b b0 c2 64 b3 72 ea 8b 87 40 09 b4 82 b4 ad 76 f9 36 05 60 89 c8 20 eb a5 f1
0b: 51 0b 27 a7 f0 76 84 96 eb b1 2e c2 85 28 bc 48 34 d4 01 8d 5b 25 54 e0 c4 4f a0 fa 99 8d 6d 7a
0c: 64 b1 a9 5d a4 f9 f5 22 eb 9a f4 a8 7a 78 4b 7f e2 8b 04 50 43 7d 26 2d 19 98 38 6a 4f 2d 30 15
0d: 2e 4f cd b9 ce 9e 8d 12 c9 fe 33 8b 84 ce 5b 40 e3 7f 72 6d 6c 8a 6a 9e 54 f1 e3 64 5d 6e 7f ac
0e: 1a e7 f7 fa 00 22 ed 2b 23 fa 58 c5 eb 44 92 5d cc aa 82 9f 23 fb a6 c9 65 2a e0 79 12 65 2c 34
0f: c5 23 16 c9 cc 05 30 f3 96 0b 90 67 1a a7 69 4c 3e 43 24 9d 4e 68 bd 8b 75 6e 9d 07 6f 1a 6a ba
```

## KEYS_1_PS4

16 rows x 32 bytes. Aeropause byte i uses row i, column key1_off (0..31). PS4 10.0.

```
00: c8 48 c2 b4 08 eb 88 f7 5f 4a 09 2d 59 1f 09 cd 1c 18 f4 7a 28 4a 96 6d b3 59 71 53 75 7e 82 50
01: 57 e4 59 b3 f4 49 69 40 eb 17 c9 9f 17 97 71 ae c9 60 7f f8 2e 08 94 e8 43 ea da 6b e5 19 59 33
02: 1f 89 ae 47 57 7b 1c 66 fe ff 95 bf 55 6b d5 93 27 ea a6 24 67 39 9f d3 0c aa 26 42 e7 66 4d d8
03: 18 75 fe 44 03 46 ee 3e f8 3c b7 85 97 03 07 06 92 ff 59 17 27 0b 21 f7 05 7f 69 90 0e 38 91 c6
04: 67 23 48 ba 08 8e 57 dd 91 d0 40 47 1c 5b bf c8 06 3f 96 a0 dc 00 e5 9a f5 3b 90 80 66 bb 0f 93
05: 30 07 2b 56 45 a0 9a b1 b0 72 0c e4 dd 70 dd 7c 5a bf d4 e8 0d ca 37 e5 0e fd 12 ee 79 9a 5e a7
06: 1e 31 af 1f 46 52 ca f3 42 00 3d f2 89 7c 1c 77 60 b7 4a 80 76 47 4b 3f b6 91 2f 9c c2 f1 ad 44
07: 29 cb 32 8c 0a 8d 05 75 46 a1 f8 da 1a a7 20 de 32 59 fe 70 b5 87 f3 92 fd b4 df f4 a6 e3 7d 98
08: 3b e1 ba 18 db 61 d1 c2 a6 ee 08 25 fa 86 8a 7b fe bc 02 bd 22 5f 25 30 51 5d 28 36 5a 29 8e 52
09: eb 49 14 28 7f 0a c4 69 25 85 6b ec 33 33 57 ab 50 13 cf e7 73 78 23 06 4d 1f bb 38 11 b1 6e 6c
0a: 6f cd 4b 0e 42 85 80 bb a8 39 68 c9 5b bb 46 58 86 88 57 88 5e ea 7c 37 df fd 02 39 45 89 2e f2
0b: e4 f0 08 c0 b6 eb 9c 6e 7a 81 a3 26 46 fe e1 70 3c 3e 11 7a 32 ce 45 02 7d 32 cd 08 07 06 a3 a8
0c: f7 34 fe ee 06 b0 14 d6 6b 2d 2e 01 af 77 11 ec 1f 31 38 17 9c d0 e0 c5 4d a4 d6 ad b9 e6 e1 e3
0d: e2 9e 44 91 9a 5e 26 ca cc da 4d d7 78 6a 75 a6 19 ad cc 62 c7 b6 0d 14 b1 be eb cb 10 cf a9 ee
0e: e2 42 08 35 8a 5c bc f1 49 fe 64 78 03 49 0c 85 f0 e4 77 26 d2 5e f5 c1 3b 3d 2d cc cf 2a ac ed
0f: 88 52 74 6d bf b2 b9 f7 58 51 1c 50 f6 3d f2 c4 47 0a 21 30 47 81 2d e4 75 0d 8f 2d 22 b0 63 27
```

## KEYS_1_PS5

Same for PS5.

```
00: 79 4d 78 30 fe 10 52 4c a8 90 5b 9a 7e 5f d3 e1 13 e0 f1 0f a3 e7 bb 45 7f dc 8e d5 f1 04 5c 78
01: 51 ef f8 65 59 03 39 84 37 ae 59 df 23 b6 60 34 e6 4b e2 f5 4c 13 c6 da f9 fd b3 65 84 d6 45 ec
02: 2c 00 f2 ed dc cb 93 6e 61 46 e5 d6 01 94 ee 78 85 0e 68 5e b5 5b cd d3 63 41 fc 81 43 1c 6f 7c
03: ba e8 bd 86 31 d5 70 7f b5 4a 90 3e 84 e1 71 e0 02 99 f4 71 e7 02 ed 36 af de 56 c2 90 e0 ae c2
04: f9 af 53 c6 d8 62 16 32 27 fb 6e 9b 48 c6 ea ff 6f 78 02 22 98 2c 1f bf b0 8e a9 39 bc df 17 de
05: d7 0e e1 7a 01 0e c3 87 fc aa e4 6b 0f 5b 0a f1 18 19 8a e5 2c 36 9b 40 30 99 24 94 48 d7 47 b2
06: af 6b 8c 40 9e 4d 6d 34 07 c1 26 2f bb 14 f7 bc 36 52 bd 84 fe 4a 9a f4 8a db 34 89 aa f1 0d 94
07: 0b 92 f4 1c e4 6c 79 2d 6e c0 19 0a d5 55 94 14 05 13 c2 62 23 b3 d4 26 c4 44 56 7a cd 1c ea d4
08: 74 b9 36 40 9f 08 fb 49 62 05 92 98 ad 1d 9f 8a 76 8b d4 0f 21 40 76 b6 16 91 45 93 66 cc 12 ea
09: 4d f4 09 e2 ac 33 d0 6f 43 51 07 3e d7 95 2c 1e 1f 0c 24 b3 0e 3a ef 95 f5 eb 77 dd 20 f2 35 98
0a: f2 ae a9 66 e6 13 ef 5d 3a 2d 66 ed e2 1e e9 32 4a 40 bf 37 c6 70 29 d9 8c a1 61 4a 29 3d c7 55
0b: 9c 94 9e c9 11 45 10 28 a7 27 d1 d3 d0 84 79 c7 a9 b0 f6 af 45 8c 3c d4 df 3b f7 0d a2 4f 13 97
0c: 78 27 f0 48 c0 a5 ab 83 01 05 d0 12 d7 1e 12 3a 4e 98 77 ae ba b1 4e b5 3b 59 ca 6d a5 11 80 91
0d: 9c 07 69 59 5a 53 70 7c 95 97 11 6d 66 8d a3 bd bb 2d b0 bf 9b 10 cb c7 0f 5b 7e 67 e2 b0 4b ba
0e: 10 12 b9 bc 97 fd 48 e4 8a c1 0f a1 30 9d 56 20 24 1a 7d 5b a0 b4 be 9d 38 4f b4 56 a8 4d 13 7c
0f: 44 e8 84 97 eb 78 2c 52 85 e4 a2 f6 f3 d9 71 9e ee b8 11 47 fb a9 1b c7 40 c6 e1 19 6d 50 a1 2a
```

## Checksums

| Table | SHA-256 of the listed bytes |
|---|---|
| KEYS_A_PS4 | `25c47ab6345f3a8f55ff6cbcb68b22ac44c726777af68b0314320bfc3fb0882d` |
| KEYS_A_PS5 | `5fa8c0190035851fca08dc71edee9f0ac7f3a165c190345608fcf864d5e6ae50` |
| KEYS_B_PS4 | `e553c18a73f942ed244f088c6adbcb2997e8ee0a9f5deaedd4a5dcf883b312b8` |
| KEYS_B_PS5 | `6b0abb2b8645a603fa069eaa4f167d390f43743ada89f8c772e2c73f49d74cf1` |
| KEYS_0_PS4 | `e7d94548c4b9dba6076a89ba1dfb466cf7b635fcbc4e9efc8ec844d946c6df75` |
| KEYS_0_PS5 | `53b11608360076562655967ca89d6bd5e9b8c05220644075bb49876c95cd3965` |
| KEYS_1_PS4 | `4aacf71fe36d53b46faa277a04c73e3c115eb3d4b4ae7732e919a6d1b35f5f54` |
| KEYS_1_PS5 | `3299d292a30e9be23c16a79bdd1a7cf8862a973ead751fd20d6d7c48154edeb0` |
