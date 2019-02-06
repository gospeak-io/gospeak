# Gospeak

[![CircleCI](https://circleci.com/gh/loicknuchel/gospeak/tree/master.svg?style=shield)](https://circleci.com/gh/loicknuchel/gospeak/tree/master)
[![Codacy Badge](https://img.shields.io/codacy/grade/45ed63364ff14a87b7f1dad81ffee091.svg)](https://www.codacy.com/app/loicknuchel/gospeak?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=loicknuchel/gospeak&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://img.shields.io/codacy/coverage/45ed63364ff14a87b7f1dad81ffee091.svg)](https://www.codacy.com/app/loicknuchel/gospeak?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=loicknuchel/gospeak&amp;utm_campaign=Badge_Grade)

A website to help meetup orga with their daily tasks

## Features

- For orga
    - Create your group and invite other orga
    - Setup a CFP so speakers can propose their talks
    - Create events and add proposed talks
    - Find public talks and ask for proposal
- For speakers
    - Create a talk and propose it to available CFPs
    - Make your talk public so orgas can ask for proposal

## Dev

Run coverage: `sbt clean coverage test coverageReport coverageAggregate && xdg-open target/scala-2.12/scoverage-report/index.html`
