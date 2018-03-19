package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.Externalizer;
import com.day.cq.wcm.api.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.cru.contentscoring.core.models.ContentScore;
import org.cru.contentscoring.core.models.ScoreType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.API_ENDPOINT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.ERROR_EMAIL_RECIPIENTS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.EXTERNALIZERS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.MAX_RETRIES;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.WAIT_TIME;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ContentScoreUpdateServiceImplTest {
    private static final String UNAWARE_SCORE = "1";
    private static final String CURIOUS_SCORE = "2";
    private static final String FOLLOWER_SCORE = "5";
    private static final String GUIDE_SCORE = "3";

    private static final Map<String, String> CONFIGURED_EXTERNALIZERS = buildExternalizers();

    @InjectMocks
    private ContentScoreUpdateServiceImpl updateService;

    @Test
    public void testActivation() {
        Map<String, Object> config = Maps.newHashMap();
        config.put(API_ENDPOINT, "http://somewhere-out.there.com");
        config.put(WAIT_TIME, 10000L);
        config.put(MAX_RETRIES, 5);
        config.put(ERROR_EMAIL_RECIPIENTS, "some.email@example.com,another.email@example.com");

        List<String> configExternalizers = Lists.newArrayList();
        configExternalizers.add("/content/test/us/en=test-publish");
        configExternalizers.add("/content/foo/us/en=foo-publish");
        config.put(EXTERNALIZERS, configExternalizers);

        updateService.activate(config);
        assertThat(ContentScoreUpdateServiceImpl.internalQueueManager, is(not(nullValue())));
        assertThat(ContentScoreUpdateServiceImpl.queueManagerThread, is(not(nullValue())));

        assertThat(updateService.externalizersConfigs, is(not(nullValue())));
        assertThat(updateService.externalizersConfigs, is(equalTo(CONFIGURED_EXTERNALIZERS)));
    }

    private static Map<String, String> buildExternalizers() {
        Map<String, String> externalizers = Maps.newHashMap();
        externalizers.put("/content/test/us/en", "test-publish");
        externalizers.put("/content/foo/us/en", "foo-publish");

        return externalizers;
    }

    @Test
    public void testPageHasAllScores() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "1");

        assertThat(updateService.hasAllScores(pageProperties), is(true));
    }

    @Test
    public void testPageMissingAllScores() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put("someProperty", "someValue");

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testPageMissingUnawareScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "1");

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testPageMissingCuriousScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "0.75");

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testPageMissingFollowerScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "0.51");

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testPageMissingGuideScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "0.99");

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testPageMissingConfidence() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);

        assertThat(updateService.hasAllScores(pageProperties), is(false));
    }

    @Test
    public void testCreateAllScores() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "0.01");

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(Integer.parseInt(UNAWARE_SCORE))));
        assertThat(contentScore.getCurious(), is(equalTo(Integer.parseInt(CURIOUS_SCORE))));
        assertThat(contentScore.getFollower(), is(equalTo(Integer.parseInt(FOLLOWER_SCORE))));
        assertThat(contentScore.getGuide(), is(equalTo(Integer.parseInt(GUIDE_SCORE))));
        assertThat(contentScore.getConfidence(), is(equalTo(new BigDecimal("0.01"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), "10");

        updateService.createScore(pageProperties);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConfidence() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);
        pageProperties.put(ScoreType.CONFIDENCE.getPropertyName(), "1.01");

        updateService.createScore(pageProperties);
    }

    @Test
    public void testSetContentScoreUpdatedDate() throws RepositoryException {
        Page mockPage = mock(Page.class);
        Resource mockResource = mock(Resource.class);
        when(mockPage.getContentResource()).thenReturn(mockResource);

        Node mockNode = mock(Node.class);
        when(mockResource.adaptTo(Node.class)).thenReturn(mockNode);

        Session mockSession = mock(Session.class);
        doNothing().when(mockSession).refresh(true);
        doNothing().when(mockSession).save();
        when(mockNode.getSession()).thenReturn(mockSession);

        updateService.setContentScoreUpdatedDate(mockPage);

        verify(mockNode, times(1)).setProperty(eq(CONTENT_SCORE_UPDATED), any(Calendar.class));
    }

    @Test
    public void testSetContentScoreUpdateDateRetry() throws RepositoryException {
        Page mockPage = mock(Page.class);
        Resource mockResource = mock(Resource.class);
        when(mockPage.getContentResource()).thenReturn(mockResource);

        Node mockNode = mock(Node.class);
        when(mockResource.adaptTo(Node.class)).thenReturn(mockNode);

        Session mockSession = mock(Session.class);
        doThrow(new RepositoryException()).when(mockSession).refresh(true);
        when(mockNode.getSession()).thenReturn(mockSession);

        updateService.setContentScoreUpdatedDate(mockPage);

        verify(mockSession).refresh(false);
        verify(mockNode).setProperty(eq(CONTENT_SCORE_UPDATED), any(Calendar.class));
        verify(mockSession).save();
    }

    @Test
    public void testGetVanityUrl() {
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(pagePath, vanityPath, "https://vanity.com/content/test/us/en/" + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        String vanityUrl = updateService.getPageUrl(page);
        assertThat(vanityUrl, is(equalTo("https://vanity.com/" + vanityPath)));
    }

    @Test
    public void testGetPageUrl() {
        String pagePath = "/content/test/us/en/page-path";
        Page page = mockPage(pagePath, pagePath, "https://page.com" + pagePath);

        String pageUrl = updateService.getPageUrl(page);
        assertThat(pageUrl, is(equalTo("https://page.com" + pagePath + ".html")));
    }

    private Page mockPage(final String pagePath, final String externalizerPath, final String externalLink) {
        Page page = mock(Page.class);
        when(page.getPath()).thenReturn(pagePath);

        Resource resource = mock(Resource.class);
        when(page.adaptTo(Resource.class)).thenReturn(resource);

        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resource.getResourceResolver()).thenReturn(resolver);

        Externalizer externalizer = mock(Externalizer.class);
        when(resolver.adaptTo(Externalizer.class)).thenReturn(externalizer);

        when(externalizer.externalLink(resolver, "test-publish", externalizerPath))
            .thenReturn(externalLink);

        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        return page;
    }

    @Test
    public void testGetExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/foo/us/en/some-page";
        String[] configuration = updateService.getPublishConfiguration(path);

        assertThat(configuration, is(not(nullValue())));
        assertThat(configuration[0], is(equalTo("/content/foo/us/en")));
        assertThat(configuration[1], is(equalTo("foo-publish")));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNonExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/fail/us/en/some-page";
        updateService.getPublishConfiguration(path);
    }
}
