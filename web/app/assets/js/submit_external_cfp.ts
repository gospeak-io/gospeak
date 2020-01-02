// look for potential duplicates and display them
(function () {
    interface DuplicatesQuery {
        cfpUrl?: string
        cfpName?: string
        cfpEndDate?: string
        eventUrl?: string
        eventStartDate?: string
        twitterAccount?: string
        twitterHashtag?: string
    }

    interface ExternalCfp {
        id: string
        url: string
        name: string
        description: string
        logo?: string
        begin?: string
        close?: string
        eventUrl?: string
        eventStart?: string
        eventFinish?: string
        eventLocation?: string
        eventTwitterAccount?: string
        eventTwitterHashtag?: string
        tags: string[]
    }

    const $duplicateContainer = $('#duplicate-cfps');
    const $cfpUrl = $('input#url');
    const $cfpName = $('input#name');
    const $cfpEndDate = $('input#close');
    const $eventUrl = $('input#event_url');
    const $eventStartDate = $('input#event_start');
    const $twitterAccount = $('input#event_twitterAccount');
    const $twitterHashtag = $('input#event_twitterHashtag');
    const $inputsToWatch = [$cfpUrl, $cfpName, $cfpEndDate, $eventUrl, $eventStartDate, $twitterAccount, $twitterHashtag];

    $inputsToWatch.forEach(function ($input) {
        $input.on('change', function () {
            if ($(this).val()) {
                findDuplicates().then(renderDuplicates);
            }
        });
    });

    function findDuplicates(): Promise<ExternalCfp[]> {
        const query: DuplicatesQuery = {
            cfpUrl: $cfpUrl.val(),
            cfpName: $cfpName.val(),
            cfpEndDate: $cfpEndDate.val(),
            eventUrl: $eventUrl.val(),
            eventStartDate: $eventStartDate.val(),
            twitterAccount: $twitterAccount.val(),
            twitterHashtag: $twitterHashtag.val()
        };
        const params = Object.keys(query).filter(key => !!query[key]).map(key => key + '=' + encodeURI(query[key]));
        const url = '/ui/cfps/duplicates?' + params.join('&');
        if (params.length > 0) {
            return $.get(url).then(res => res.data);
        } else {
            return Promise.resolve([]);
        }
    }

    function renderDuplicates(cfps: ExternalCfp[]) {
        if (cfps.length > 0) {
            $duplicateContainer.html(
                '<h3>Potential duplicates</h3>\n' +
                '<p>Please check to not add a duplicate</p>\n' +
                cfps.map(cfp =>
                    `<div class="alert alert-primary" role="alert">
                        <a href="/cfps/ext/${cfp.id}" target="_blank">${cfp.name}</a>
                    </div>`
                ).join('\n')
            );
        } else {
            $duplicateContainer.html('');
        }
    }
})();


// parse CFP website and fill form
(function () {
    // TODO
})();
