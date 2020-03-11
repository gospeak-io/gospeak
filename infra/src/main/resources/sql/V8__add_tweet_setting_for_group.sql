ALTER TABLE group_settings
    ADD COLUMN proposal_tweet VARCHAR NOT NULL default 'Presentation of "{{proposal.title}}" by{{#proposal.speakers}}{{^-first}} and{{/-first}} {{#links.twitter}}{{handle}}{{/links.twitter}}{{^links.twitter}}{{name}}{{/links.twitter}}{{/proposal.speakers}}';
