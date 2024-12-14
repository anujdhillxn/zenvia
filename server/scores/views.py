from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework import status
from django.utils.dateparse import parse_date
from django.db.models import Q

from duos.models import Duo
from .models import Score
from .serializers import ScoreSerializer

class RetrieveScoreView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        user = request.user
        start_date_str = request.query_params.get('start_date')
        end_date_str = request.query_params.get('end_date')
        
        if not start_date_str or not end_date_str:
            return Response({'error': 'start_date and end_date parameters are required'}, status=status.HTTP_400_BAD_REQUEST)
        
        start_date = parse_date(start_date_str)
        end_date = parse_date(end_date_str)
        
        if not start_date or not end_date:
            return Response({'error': 'Invalid date format'}, status=status.HTTP_400_BAD_REQUEST)
        scores = Score.objects.filter(user=user, date__range=[start_date, end_date])
        serializer = ScoreSerializer(scores, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

class UpdateScoreView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        user = request.user
        scores_data = request.data.get('scores')
        if not scores_data:
            return Response({'error': 'Scores data is required'}, status=status.HTTP_400_BAD_REQUEST)
        confirmed_duo = Duo.objects.filter(
            (Q(user1=user) | Q(user2=user))
        ).first()
        if not confirmed_duo:
            return Response({'error': 'User is not part of a confirmed duo'}, status=status.HTTP_403_FORBIDDEN)
        response_data = []
        for score_data in scores_data:
            date_str = score_data.get('date')
            value = score_data.get('value')
            uninterrupepted_tracking = score_data.get('uninterrupted_tracking')
            if not date_str or not value or uninterrupepted_tracking is None:
                return Response({'error': 'Date, uninterrupted_tracking and value are required for each score entry'}, status=status.HTTP_400_BAD_REQUEST)
            date = parse_date(date_str)
            if not date:
                return Response({'error': f'Invalid date format for date: {date_str}'}, status=status.HTTP_400_BAD_REQUEST)
            #if score with date already exists, update the value
            score = Score.objects.filter(user=user, date=date).first()
            if score:
                score.value = value
                score.uninterrupted_tracking = uninterrupepted_tracking
                score.save()
            else:
                score = Score.objects.create(user=user, date=date, value=value, uninterrupted_tracking=uninterrupepted_tracking)
            serializer = ScoreSerializer(score)
            response_data.append(serializer.data)
        
        return Response(response_data, status=status.HTTP_200_OK)